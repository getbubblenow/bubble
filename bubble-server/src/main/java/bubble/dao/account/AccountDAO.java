package bubble.dao.account;

import bubble.dao.account.message.AccountMessageDAO;
import bubble.dao.app.*;
import bubble.dao.cloud.AnsibleRoleDAO;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleFootprintDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.*;
import bubble.model.app.*;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.model.HashedPassword;
import org.glassfish.grizzly.http.server.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static bubble.ApiConstants.getRemoteHost;
import static bubble.model.account.AccountTemplate.copyTemplateObjects;
import static bubble.model.account.AutoUpdatePolicy.EMPTY_AUTO_UPDATE_POLICY;
import static org.cobbzilla.util.daemon.ZillaRuntime.daemon;
import static org.cobbzilla.wizard.model.IdentifiableBase.CTIME_ASC;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository @Slf4j
public class AccountDAO extends AbstractCRUDDAO<Account> {

    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private BubbleAppDAO appDAO;
    @Autowired private AppSiteDAO siteDAO;
    @Autowired private AppMatcherDAO matchDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private AppDataDAO dataDAO;
    @Autowired private RuleDriverDAO driverDAO;
    @Autowired private AnsibleRoleDAO roleDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleFootprintDAO footprintDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private AccountMessageDAO messageDAO;
    @Autowired private DeviceDAO deviceDAO;

    public Account newAccount(Request req, AccountRegistration request, Account parent) {
        return create(new Account(request)
                .setRemoteHost(getRemoteHost(req))
                .setParent(parent.getUuid())
                .setPolicy(new AccountPolicy().setContact(request.getContact())));
    }

    public Account findByName(String name) { return findByUniqueField("name", name); }

    public Account findById(String id) {
        final Account found = findByUuid(id);
        return found != null ? found : findByUniqueField("name", id);
    }

    @Override public Object preCreate(Account account) {
        if (!account.admin() && account.hasReservedName()) throw invalidEx("err.name.reserved");
        if (account.getAutoUpdatePolicy() == null) {
            account.setAutoUpdatePolicy(new AutoUpdatePolicy());
        }
        return super.preCreate(account);
    }

    @Override public Account postCreate(Account account, Object context) {

        final String accountUuid = account.getUuid();
        if (account.hasPolicy()) {
            policyDAO.create(new AccountPolicy(account.getPolicy()).setAccount(accountUuid));
        }

        // create an uninitialized device for the account
        final BubbleNode thisNode = configuration.getThisNode();
        if (thisNode != null) {
            deviceDAO.ensureSpareDevice(accountUuid, thisNode.getNetwork(), true);
        }

        if (account.hasParent()) {
            final AccountInitializer init = new AccountInitializer(account, this, messageDAO);
            account.setAccountInitializer(init);
            daemon(init);
        }

        return super.postCreate(account, context);
    }

    @Override public Account postUpdate(Account account, Object context) {
        if (account.hasPolicy()) {
            final AccountPolicy policy = policyDAO.findSingleByAccount(account.getUuid());
            policy.update(account.getPolicy());
            policyDAO.update(policy);
        }
        return super.postUpdate(account, context);
    }

    public void copyTemplates(Account account, AtomicBoolean ready) {
        final String parent = account.getParent();
        final String acct = account.getUuid();

        final Map<String, CloudService> clouds = new HashMap<>();
        copyTemplateObjects(acct, parent, cloudDAO, new AccountTemplate.CopyTemplate<>() {
            @Override public CloudService preCreate(CloudService parentEntity, CloudService accountEntity) {
                return accountEntity.setDelegated(parentEntity.getUuid())
                        .setCredentials(CloudCredentials.delegate(configuration.getThisNode(), configuration))
                        .setTemplate(false);
            }
            @Override public void postCreate(CloudService parentEntity, CloudService accountEntity) {
                clouds.put(parentEntity.getUuid(), accountEntity);
            }
        });

        copyTemplateObjects(acct, parent, footprintDAO);

        //noinspection Convert2Diamond -- compilation breaks with <>
        copyTemplateObjects(acct, parent, domainDAO, new AccountTemplate.CopyTemplate<BubbleDomain>() {
            @Override public BubbleDomain preCreate(BubbleDomain parentEntity, BubbleDomain accountEntity) {
                final CloudService publicDns = findDnsCloudService(parentEntity, parentEntity.getPublicDns());
                if (publicDns == null) return null;
                return accountEntity
                        .setDelegated(parentEntity.getUuid())
                        .setPublicDns(publicDns.getUuid());
            }

            public CloudService findDnsCloudService(BubbleDomain parentEntity, String cloudDnsUuid) {
                final CloudService dns = clouds.get(cloudDnsUuid);
                if (dns == null) {
                    log.error("DNS service "+ cloudDnsUuid +" could not be found for domain "+parentEntity.getUuid());
                    return null;
                }
                final CloudService acctPublicDns = cloudDAO.findByAccountAndName(acct, dns.getName());
                if (acctPublicDns == null) {
                    log.error("DNS service not found under account "+acct+": "+dns.getName());
                    return null;
                }
                return dns;
            }
        });
        ready.set(true);

        copyTemplateObjects(acct, parent, roleDAO);

        final Map<String, RuleDriver> drivers = new HashMap<>();
        copyTemplateObjects(acct, parent, driverDAO, new AccountTemplate.CopyTemplate<>() {
            @Override public void postCreate(RuleDriver parentEntity, RuleDriver accountEntity) {
                drivers.put(parentEntity.getUuid(), accountEntity);
            }
        });

        copyTemplateObjects(acct, parent, appDAO, new AccountTemplate.CopyTemplate<>() {
            @Override public void postCreate(BubbleApp parentApp, BubbleApp accountApp) {
                final Map<String, AppRule> rules = new HashMap<>();
                final Map<String, AppMatcher> matchers = new HashMap<>();
                final Map<String, AppSite> sites = new HashMap<>();
                copyTemplateObjects(acct, parent, siteDAO, new AccountTemplate.CopyTemplate<>() {
                    @Override public AppSite preCreate(AppSite parentEntity, AppSite accountEntity) {
                        return accountEntity.setApp(accountApp.getUuid());
                    }
                    @Override public void postCreate(AppSite parentEntity, AppSite accountEntity) {
                        sites.put(parentEntity.getUuid(), accountEntity);
                    }
                });

                copyTemplateObjects(acct, parent, ruleDAO, new AccountTemplate.CopyTemplate<>() {
                    @Override public AppRule preCreate(AppRule parentEntity, AppRule accountEntity) {
                        return accountEntity
                                .setApp(accountApp.getUuid())
                                .setDriver(drivers.get(parentEntity.getDriver()).getUuid());
                    }
                    @Override public void postCreate(AppRule parentEntity, AppRule accountEntity) {
                        rules.put(parentEntity.getUuid(), accountEntity);
                    }
                });

                copyTemplateObjects(acct, parent, matchDAO, new AccountTemplate.CopyTemplate<>() {
                    @Override public AppMatcher preCreate(AppMatcher parentEntity, AppMatcher accountEntity) {
                        return accountEntity
                                .setApp(accountApp.getUuid())
                                .setSite(sites.get(parentEntity.getSite()).getUuid())
                                .setRule(rules.get(parentEntity.getRule()).getUuid());
                    }
                    @Override public void postCreate(AppMatcher parentEntity, AppMatcher accountEntity) {
                        matchers.put(parentEntity.getUuid(), accountEntity);
                    }
                });

                copyTemplateObjects(acct, parent, dataDAO, new AccountTemplate.CopyTemplate<>() {
                    @Override public AppData preCreate(AppData parentEntity, AppData accountEntity) {
                        return accountEntity
                                .setMatcher(matchers.get(parentEntity.getMatcher()).getUuid())
                                .setSite(sites.get(parentEntity.getSite()).getUuid())
                                .setApp(accountApp.getUuid());
                    }
                });
            }
        });

        log.info("copyTemplates completed: "+acct);
    }

    @Override public void delete(String uuid) {
        final Account account = findByUuid(uuid);

        // you cannot delete the account that owns the current network
        if (account.getUuid().equals(configuration.getThisNetwork().getAccount())) {
            throw invalidEx("err.delete.invalid", "cannot delete account ("+account.getUuid()+") that owns current network ("+configuration.getThisNetwork().getUuid()+")", account.getUuid());
        }

        // todo: cannot delete an account that has unpaid bills

        final AccountPolicy policy = policyDAO.findSingleByAccount(uuid);

        configuration.getEntityClassesReverse().forEach(c -> {
            if (HasAccount.class.isAssignableFrom(c)) {
                final AccountOwnedEntityDAO dao = (AccountOwnedEntityDAO) configuration.getDaoForEntityClass(c);
                dao.handleAccountDeletion(uuid);
            }
        });

        switch (policy.getDeletionPolicy()) {
            case full_delete:
                super.delete(uuid);
                break;
            case block_delete: default:
                update(account.setParent(null)
                        .setAdmin(null)
                        .setSuspended(null)
                        .setDescription(null)
                        .setUrl(null)
                        .setAutoUpdatePolicy(EMPTY_AUTO_UPDATE_POLICY)
                        .setHashedPassword(HashedPassword.DELETED));
                break;
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean activated() { return countAll() > 0; }

    public Account findFirstAdmin() {
        final List<Account> admins = findByField("admin", true);
        if (admins.isEmpty()) return null;
        admins.sort(CTIME_ASC); // todo: do this in SQL
        return admins.get(0);
    }
}
