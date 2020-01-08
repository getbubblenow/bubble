package bubble.dao.cloud;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository @Slf4j
public class BubbleDomainDAO extends AccountOwnedTemplateDAO<BubbleDomain> {

    @Autowired private AnsibleRoleDAO roleDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired private BubbleConfiguration configuration;

    @Override public Object preCreate(BubbleDomain domain) {

        final String[] roles = domain.getRoles();
        if (roles == null || roles.length == 0) throw invalidEx("err.roles.required");

        final CloudService dnsService = cloudDAO.findByUuid(domain.getPublicDns());
        if (dnsService == null) throw invalidEx("err.dns.notFound", "cloud service not found: "+domain.getPublicDns(), domain.getPublicDns());

        final Account account = accountDAO.findByUuid(domain.getAccount());
        final List<String> validRoles = new ArrayList<>();
        for (String r : roles) {
            if (roleDAO.findByAccountAndId(domain.getAccount(), r) == null) {
                if (!account.hasParent()) {
                    validRoles.add(r);
                } else if (roleDAO.findPublicTemplate(account.getParent(), r) == null) {
                    continue;
                }
                validRoles.add(r);
            } else {
                validRoles.add(r);
            }
        }

        domain.setRoles(validRoles.toArray(new String[0]));
        return super.preCreate(domain);
    }

    @Override public BubbleDomain postCreate(BubbleDomain domain, Object context) {
        final CloudService dnsService = cloudDAO.findByUuid(domain.getPublicDns());
        if (!dnsService.delegated()) dnsService.getDnsDriver(configuration).create(domain);
        return super.postCreate(domain, context);
    }

    public BubbleDomain findByFqdn(String fqdn) {
        if (fqdn == null) {
            log.debug("findByFqdn: returning default domain for null fqdn");
            return findByUuid(configuration.getThisNode().getDomain());
        }
        final String[] parts = fqdn.split("\\.");
        if (parts.length < 2) {
            log.error("findByFqdn: invalid fqdn: "+fqdn);
            return null;
        }
        final StringBuilder dname = new StringBuilder(parts[parts.length-1]);
        for (int i=parts.length-2; i>=1; i--) {
            dname.insert(0, ".").insert(0, parts[i]);
            final BubbleDomain domain = findByAccountAndId(configuration.getThisNode().getAccount(), dname.toString());
            if (domain != null) return domain;
        }
        final BubbleDomain defaultDomain = findByUuid(configuration.getThisNode().getDomain());
        return fqdn.endsWith("."+defaultDomain) ? defaultDomain : null;
    }

    public List<BubbleDomain> findDelegated(String domainUuid) { return findByField("delegated", domainUuid); }

}
