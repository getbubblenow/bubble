/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.account;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;
import bubble.cloud.compute.ComputeNodeSizeType;
import bubble.dao.account.message.AccountMessageDAO;
import bubble.dao.app.*;
import bubble.dao.bill.AccountPaymentArchivedDAO;
import bubble.dao.bill.BillDAO;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleFootprintDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.*;
import bubble.model.app.*;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.*;
import bubble.server.BubbleConfiguration;
import bubble.service.SearchService;
import bubble.service.account.SyncAccountService;
import bubble.service.boot.SelfNodeService;
import bubble.service.device.DeviceService;
import bubble.service.stream.RuleEngineService;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.cache.Refreshable;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.dao.SqlViewSearchableDAO;
import org.cobbzilla.wizard.model.HashedPassword;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.grizzly.http.server.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import javax.transaction.Transactional;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static bubble.ApiConstants.getRemoteHost;
import static bubble.model.account.Account.ROOT_EMAIL;
import static bubble.model.account.Account.ROOT_USERNAME;
import static bubble.model.account.AccountTemplate.copyTemplateObjects;
import static bubble.model.account.AutoUpdatePolicy.EMPTY_AUTO_UPDATE_POLICY;
import static bubble.server.BubbleConfiguration.getDEFAULT_LOCALE;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.wizard.model.IdentifiableBase.CTIME_ASC;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;
import static org.hibernate.criterion.Restrictions.isNotNull;

@Repository @Slf4j
public class AccountDAO extends AbstractCRUDDAO<Account> implements SqlViewSearchableDAO<Account> {

    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private BubbleAppDAO appDAO;
    @Autowired private AppSiteDAO siteDAO;
    @Autowired private AppMatcherDAO matchDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private AppDataDAO dataDAO;
    @Autowired private AppMessageDAO appMessageDAO;
    @Autowired private RuleDriverDAO driverDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleFootprintDAO footprintDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private AccountMessageDAO messageDAO;
    @Autowired private DeviceDAO deviceDAO;
    @Autowired private SelfNodeService selfNodeService;
    @Autowired private SearchService searchService;
    @Autowired private SyncAccountService syncAccountService;
    @Autowired private ReferralCodeDAO referralCodeDAO;
    @Autowired private DeviceService deviceService;
    @Autowired private RuleEngineService ruleEngineService;

    public Account newAccount(Request req, Account caller, AccountRegistration request, Account parent) {
        final AccountContact contact = new AccountContact()
                .setType(CloudServiceType.email)
                .setInfo(request.getEmail())
                .setRemovable(false)
                .setReceiveInformationalMessages(bool(request.getReceiveInformationalMessages()))
                .setReceivePromotionalMessages(bool(request.getReceivePromotionalMessages()));

        return create(new Account(request)
                .setAdmin(caller != null && caller.admin() && request.admin())   // only admins can create other admins
                .setRemoteHost(getRemoteHost(req))
                .setParent(parent.getUuid())
                .setPolicy(new AccountPolicy().setContact(contact, null, configuration)));
    }

    public Account findByEmail(String email) {
        if (email.equals(ROOT_EMAIL)) email = ROOT_USERNAME;
        return findByUniqueField("email", email.trim());
    }

    public Account findById(String id) {
        final Account found = findByUuid(id);
        return found != null ? found : findByUniqueField("email", id);
    }

    @Override public Object preCreate(Account account) {
        if (!account.hasLocale()) account.setLocale(getDEFAULT_LOCALE());
        account.setShowBlockStats(account.showBlockStats());  // ensure non-null

        // check account limit for plan, if there is a plan
        final BubblePlan plan = selfNodeService.getThisPlan();
        if (plan != null && plan.hasMaxAccounts()) {
            final int numAccounts = countNotDeleted();
            if (numAccounts >= plan.getMaxAccounts()) {
                throw invalidEx("err.plan.planMaxAccountLimit", "Account limit for plan reached (max "+plan.getMaxAccounts()+" accounts)");
            }
        }

        // if activated is false, then we are creating the root account
        // do not validate, it's not a valid email address and that is ok.
        final ValidationResult result = !activated() ? new ValidationResult() : account.validateEmail();
        if (result.isInvalid()) throw invalidEx(result);

        if (account.getAutoUpdatePolicy() == null) {
            account.setAutoUpdatePolicy(new AutoUpdatePolicy());
        }

        ensureThisNodeExists();
        return super.preCreate(account);
    }

    public int countNotDeleted() { return findByField("deleted", null).size(); }

    private void ensureThisNodeExists() {
        if (activated()) {
            BubbleNode thisNode = selfNodeService.getThisNode();
            if (thisNode == null) {
                log.warn("copyTemplates: thisNode not set, checking if only one node is defined");
                thisNode = selfNodeService.getSoleNode();
                if (thisNode == null) {
                    throw invalidEx("err.user.noSoleNode", "copyTemplates: thisNode was null and no sole node, cannot proceed");
                } else {
                    selfNodeService.setActivated(thisNode);
                    thisNode = selfNodeService.getThisNode();
                    if (thisNode == null) {
                        throw invalidEx("err.user.setSelfNodeFailed", "copyTemplates: thisNode not set, setActivated did not set node, cannot proceed");
                    }
                }
            }
        }
    }

    @Override public Account postCreate(@NonNull final Account account, final Object context) {
        searchService.flushCache(this);
        if (account.hasPolicy()) {
            policyDAO.create(new AccountPolicy(account.getPolicy()).setAccount(account.getUuid()));
        }

        // create an uninitialized device for the account, but only if this is a regular node network
        // sage networks do not allow devices, they launch and manage other regular node networks
        if (!account.isRoot() && !configuration.isSage()) {
            deviceDAO.ensureAllSpareDevices(account.getUuid(), configuration.getThisNetwork().getUuid());
            deviceDAO.refreshVpnUsers();
        }

        if (account.hasParent()) {
            final AccountInitializer init = new AccountInitializer(account, this, policyDAO, messageDAO, selfNodeService);
            account.setAccountInitializer(init);
            daemon(init);
        }

        return super.postCreate(account, context);
    }

    @Override public Object preUpdate(Account account) {
        final Account current = findByUuid(account.getUuid());
        if (current == null) throw notFoundEx(account.getUuid());
        account.setPreviousPasswordHash(current.getHashedPassword().getHashedPassword());
        account.setRefreshShowBlockStats(current.showBlockStats() != account.showBlockStats());
        return super.preUpdate(account);
    }

    @Override public Account postUpdate(Account account, Object context) {
        searchService.flushCache(this);
        if (account.hasPolicy()) {
            final AccountPolicy policy = policyDAO.findSingleByAccount(account.getUuid());
            policy.update(account.getPolicy());
            policyDAO.update(policy);
        }
        if (context instanceof Account) {
            final Account previousState = (Account) context;
            if (account.sync() && previousState.isHashedPasswordChanged() && !previousState.skipSync()) {
                syncAccountService.syncAccount(account);
            }
            if (previousState.isRefreshShowBlockStats()) {
                deviceService.initBlocksAndFlexRoutes(account);
                ruleEngineService.flushCaches();
            }
        }
        return super.postUpdate(account, context);
    }

    public void copyTemplates(Account account, AtomicBoolean ready) {
        final String parent = account.getParent();
        final String acct = account.getUuid();

        final Map<String, CloudService> clouds = new HashMap<>();
        copyTemplateObjects(acct, parent, cloudDAO, new AccountTemplate.CopyTemplate<>() {
            @Override public CloudService preCreate(CloudService parentEntity, CloudService accountEntity) {
                final CloudServiceDriver driver = parentEntity.getDriver();
                if (driver.disableDelegation()) {
                    return accountEntity.setTemplate(false);

                } else {
                    final BubbleNetwork thisNetwork = selfNodeService.getThisNetwork();
                    if (parentEntity.delegated()
                            && thisNetwork != null
                            && thisNetwork.getInstallType() == AnsibleInstallType.node
                            && thisNetwork.getComputeSizeType() != ComputeNodeSizeType.local) {
                        // on a node, sub-accounts can use the same cloud/config/credentials as their admin
                        return accountEntity.setDelegated(parentEntity.getDelegated())
                                .setCredentialsJson(parentEntity.getCredentialsJson())
                                .setDriverConfigJson(parentEntity.getDriverConfigJson())
                                .setTemplate(false);

                    } else {
                        return accountEntity.setDelegated(parentEntity.getUuid())
                                .setCredentials(CloudCredentials.delegate(configuration.getThisNode(), configuration))
                                .setTemplate(false);
                    }
                }
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

        cloudDAO.ensureNoopCloudsExist(account);

        final Map<String, RuleDriver> drivers = new HashMap<>();
        copyTemplateObjects(acct, parent, driverDAO, new AccountTemplate.CopyTemplate<>() {
            @Override public void postCreate(RuleDriver parentEntity, RuleDriver accountEntity) {
                drivers.put(parentEntity.getUuid(), accountEntity);
            }
        });

        final Map<String, BubbleApp> apps = new HashMap<>();
        copyTemplateObjects(acct, parent, appDAO, new AccountTemplate.CopyTemplate<>() {
            @Override public BubbleApp preCreate(BubbleApp parentApp, BubbleApp accountApp) {
                return accountApp.setTemplateApp(parentApp.getUuid());
            }
            @Override public void postCreate(BubbleApp parentApp, BubbleApp accountApp) {
                apps.put(parentApp.getUuid(), accountApp);
            }
        });

        final Map<String, AppSite> sites = new HashMap<>();
        copyTemplateObjects(acct, parent, siteDAO, new AccountTemplate.CopyTemplate<>() {
            @Override public AppSite findAccountEntity(AccountOwnedTemplateDAO<AppSite> dao, String accountUuid, AppSite parentEntity) {
                return ((AppSiteDAO) dao).findByAccountAndAppAndId(accountUuid, apps.get(parentEntity.getApp()).getUuid(), parentEntity.getName());
            }
            @Override public AppSite preCreate(AppSite parentEntity, AppSite accountEntity) {
                return accountEntity.setApp(apps.get(parentEntity.getApp()).getUuid());
            }
            @Override public void postCreate(AppSite parentEntity, AppSite accountEntity) {
                sites.put(parentEntity.getUuid(), accountEntity);
            }
        });

        final Map<String, AppRule> rules = new HashMap<>();
        copyTemplateObjects(acct, parent, ruleDAO, new AccountTemplate.CopyTemplate<>() {
            @Override public AppRule findAccountEntity(AccountOwnedTemplateDAO<AppRule> dao, String accountUuid, AppRule parentEntity) {
                return ((AppRuleDAO) dao).findByAccountAndAppAndId(accountUuid, apps.get(parentEntity.getApp()).getUuid(), parentEntity.getName());
            }
            @Override public AppRule preCreate(AppRule parentEntity, AppRule accountEntity) {
                return accountEntity
                        .setApp(apps.get(parentEntity.getApp()).getUuid())
                        .setDriver(drivers.get(parentEntity.getDriver()).getUuid());
            }
            @Override public void postCreate(AppRule parentEntity, AppRule accountEntity) {
                rules.put(parentEntity.getUuid(), accountEntity);
            }
        });

        final Map<String, AppMatcher> matchers = new HashMap<>();
        copyTemplateObjects(acct, parent, matchDAO, new AccountTemplate.CopyTemplate<>() {
            @Override public AppMatcher findAccountEntity(AccountOwnedTemplateDAO<AppMatcher> dao, String accountUuid, AppMatcher parentEntity) {
                return ((AppMatcherDAO) dao).findByAccountAndAppAndId(accountUuid, apps.get(parentEntity.getApp()).getUuid(), parentEntity.getName());
            }
            @Override public AppMatcher preCreate(AppMatcher parentEntity, AppMatcher accountEntity) {
                return accountEntity
                        .setApp(apps.get(parentEntity.getApp()).getUuid())
                        .setSite(sites.get(parentEntity.getSite()).getUuid())
                        .setRule(rules.get(parentEntity.getRule()).getUuid());
            }
            @Override public void postCreate(AppMatcher parentEntity, AppMatcher accountEntity) {
                matchers.put(parentEntity.getUuid(), accountEntity);
            }
        });

        copyTemplateObjects(acct, parent, appMessageDAO, new AccountTemplate.CopyTemplate<>() {
            @Override public AppMessage preCreate(AppMessage parentEntity, AppMessage accountEntity) {
                return accountEntity.setApp(apps.get(parentEntity.getApp()).getUuid());
            }
        });

        copyTemplateObjects(acct, parent, dataDAO, new AccountTemplate.CopyTemplate<>() {
            @Override public AppData preCreate(AppData parentEntity, AppData accountEntity) {
                return accountEntity
                        .setApp(apps.get(parentEntity.getApp()).getUuid())
                        .setMatcher(matchers.get(parentEntity.getMatcher()).getUuid())
                        .setSite(sites.get(parentEntity.getSite()).getUuid());
            }
        });

        log.info("copyTemplates completed: "+acct);
    }

    private final String NETWORK_OWNER_ACCOUNT_UUID_PARAM = "__thisNetworkOwnerAccountUuid__";

    @Override public int bulkDeleteWhere(@NonNull final String whereClause,
                                         @Nullable final Map<String, Object> parameters) {
        final Map<String, Object> enhancedParams = parameters != null ? parameters : new HashMap<>();
        enhancedParams.put(NETWORK_OWNER_ACCOUNT_UUID_PARAM, configuration.getThisNetwork().getAccount());

        return super.bulkDeleteWhere("uuid != :" + NETWORK_OWNER_ACCOUNT_UUID_PARAM + " AND (" + whereClause + ")",
                                     enhancedParams);
    }

    @Override public void delete(@Nullable final Collection<Account> accounts) {
        if (empty(accounts)) return;
        final var networkOwnerUuid = configuration.getThisNetwork().getAccount();
        if (accounts.removeIf(a -> a != null && a.getUuid().equals(networkOwnerUuid))) {
            log.warn("delete: skipping deletion of network owner's account: " + networkOwnerUuid);
        }
        super.delete(accounts);
    }

    @Override public void delete(@NonNull final String uuid) {
        // you cannot delete the account that owns the current network
        if (uuid.equals(configuration.getThisNetwork().getAccount())) {
            throw invalidEx("err.delete.invalid",
                            "cannot delete account that owns current network: "
                            + uuid + " - " + configuration.getThisNetwork().getUuid(),
                            uuid);
        }

        deleteTransactional(uuid);
        searchService.flushCache(this);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void deleteTransactional(@NonNull final String uuid) {
        // loading, and actually checking if the account with given UUID exists
        final var account = findByUuid(uuid);

        // cannot delete account with unpaid bills
        final var billDAO = configuration.getBean(BillDAO.class);
        final var unpaid = billDAO.findUnpaidByAccount(uuid);
        if (!unpaid.isEmpty()) {
            throw invalidEx("err.delete.unpaidBills",
                            "cannot delete account with unpaid bills: " + uuid + " - " + unpaid.size(),
                            uuid);
        }

        // for referral codes owned by us, set account to null, leave accountUuid in place
        final var ownedCodes = referralCodeDAO.findByAccount(uuid);
        for (var c : ownedCodes) referralCodeDAO.update(c.setAccount(null));

        // for referral a code we used, set usedBy to null, leave usedByUuid in place
        final var usedCode = referralCodeDAO.findCodeUsedBy(uuid);
        if (usedCode != null) referralCodeDAO.update(usedCode.setClaimedBy(null));

        // stash the deletion policy for later use, the policy object will be deleted in deleteDependencies
        final var deletionPolicy = policyDAO.findSingleByAccount(uuid).getDeletionPolicy();

        // archive all payment data for the account just on the first deletion request:
        configuration.getBean(AccountPaymentArchivedDAO.class).createForAccount(account);

        log.info("delete: starting to delete account-dependent objects - " + currentThread().getName());
        configuration.deleteDependencies(account);
        log.info("delete: finished deleting account-dependent objects - " + currentThread().getName());

        switch (deletionPolicy) {
            case full_delete:
                super.delete(uuid);
                return;
            default:
                // includes case block_delete
                update(account.setParent(null)
                              .setAdmin(null)
                              .setSuspended(null)
                              .setDescription(null)
                              .setDeleted()
                              .setUrl(null)
                              .setAutoUpdatePolicy(EMPTY_AUTO_UPDATE_POLICY)
                              .setHashedPassword(HashedPassword.DELETED));
                return;
        }
    }

    // once activated (any accounts exist), you can never go back
    private static final AtomicBoolean activated = new AtomicBoolean(false);
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean activated() {
        if (activated.get() && !configuration.testMode()) return true;
        final boolean accountsExist = countAll() > 0;
        if (accountsExist) {
            activated.set(true);
            configuration.refreshPublicSystemConfigs();
        }
        return accountsExist;
    }

    // once unlocked, you can never go back
    private static final AtomicBoolean unlocked = new AtomicBoolean(false);
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean locked() {
        synchronized (unlocked) {
            if (unlocked.get()) return false;
            // if any admins are unlocked, the bubble is unlocked
            final boolean anyAdminUnlocked = !findByFields("admin", true, "locked", false).isEmpty();
            if (anyAdminUnlocked) unlocked.set(true);
            return !anyAdminUnlocked;
        }
    }

    // The admin with the lowest ctime is 'root'
    // It gets looked up in a few places that may see high traffic, so we cache it under getFirstAdmin
    // Null values are not cached, getFirstAdmin will continue to call findFirstAdmin until it returns non-null,
    // then that value will be cached.
    // findFirstAdmin will always return the current value
    public static final long FIRST_ADMIN_CACHE_MILLIS = MINUTES.toMillis(20);
    private final Refreshable<Account> firstAdmin = new Refreshable<>("firstAdmin", FIRST_ADMIN_CACHE_MILLIS, this::findFirstAdmin);
    public Account getFirstAdmin() { return firstAdmin.get(); }

    public Account findFirstAdmin() {
        final List<Account> admins = findByField("admin", true);
        if (admins.isEmpty()) return null;
        admins.sort(CTIME_ASC); // todo: do this in SQL
        return admins.get(0);
    }

    // There can be only one sage account
    @Getter(lazy=true) private final Account sageAccount = findByUniqueField("sage", true);

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void unlock() {
        synchronized (unlocked) {
            final int count = bulkUpdate("locked", false);
            log.info("unlock: " + count + " accounts unlocked");
            unlocked.set(true);
            configuration.refreshPublicSystemConfigs();
        }
    }

    @NonNull public List<Account> findDeleted() {
        return list(criteria().add(isNotNull("deleted")));
    }

    @NonNull public List<Account> findNotDeleted() { return findByField("deleted", null); }

}
