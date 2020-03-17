/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.dao.cloud;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.model.bill.AccountPlan;
import bubble.model.cloud.*;
import bubble.server.BubbleConfiguration;
import bubble.service.boot.SelfNodeService;
import bubble.service.cloud.NetworkService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static bubble.model.cloud.BubbleNetwork.validateHostname;
import static bubble.server.BubbleConfiguration.getDEFAULT_LOCALE;
import static org.cobbzilla.wizard.model.Identifiable.UUID;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository @Slf4j
public class BubbleNetworkDAO extends AccountOwnedEntityDAO<BubbleNetwork> {

    @Autowired private AccountDAO accountDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private NetworkService networkService;
    @Autowired private BubbleBackupDAO backupDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private SelfNodeService selfNodeService;
    @Autowired private BubbleConfiguration configuration;

    @Override public Object preCreate(BubbleNetwork network) {
        if (!network.hasForkHost()) {
            final ValidationResult errors = validateHostname(network, accountDAO, this);
            if (errors.isInvalid()) throw invalidEx(errors);
        }
        final AnsibleInstallType installType = network.hasForkHost() && configuration.isSageLauncher()
                ? AnsibleInstallType.sage
                : AnsibleInstallType.node;
        network.setInstallType(installType);
        network.setSslPort(installType == AnsibleInstallType.sage ? 443 : configuration.getDefaultSslPort());
        if (!network.hasLocale()) network.setLocale(getDEFAULT_LOCALE());
        return super.preCreate(network);
    }

    @Override public BubbleNetwork postUpdate(BubbleNetwork network, Object context) {
        if (selfNodeService.getThisNetwork().getUuid().equals(network.getUuid())) {
            selfNodeService.refreshThisNetwork();
        }
        return super.postUpdate(network, context);
    }

    public BubbleNetwork findByDomainAndId(String domainUuid, String id) {
        final List<String> domainUuids = getAllDomainUuids(domainUuid);

        List<BubbleNetwork> found = findByFieldAndFieldIn(UUID, id, "domain", domainUuids);
        if (found != null && !found.isEmpty()) return found.get(0);

        found = findByFieldAndFieldIn("name", id, "domain", domainUuids);
        return found == null || found.isEmpty() ? null : found.get(0);
    }

    public List<BubbleNetwork> findAllByDomain(String domainUuid) {
        final List<String> domainUuids = getAllDomainUuids(domainUuid);
        return findByFieldIn("domain", domainUuids);
    }

    public List<String> getAllDomainUuids(String domainUuid) {
        final List<BubbleDomain> delegated = domainDAO.findDelegated(domainUuid);
        final List<String> domainUuids = delegated.stream().map(IdentifiableBase::getUuid).collect(Collectors.toList());
        domainUuids.add(domainUuid);
        return domainUuids;
    }

    public BubbleNetwork findByNameAndDomainName(String name, String domainName) {
        return findByUniqueFields("name", name, "domainName", domainName);
    }

    public BubbleNetwork findByNameAndDomainUuid(String name, String domainUuid) {
        return findByUniqueFields("name", name, "domain", domainUuid);
    }

    @Override public void delete(String uuid) { delete(uuid, false); }
    @Override public void forceDelete(String uuid) { delete(uuid, true); }

    public void delete(String uuid, boolean force) {
        final BubbleNetwork network = findByUuid(uuid);
        if (network == null) return;
        try {
            networkService.stopNetwork(network);
        } catch (Exception e) {
            log.warn("delete("+uuid+"): error stopping network: "+e);
        }
        try {
            if (force) {
                backupDAO.findByNetwork(network.getUuid()).forEach(b -> backupDAO.forceDelete(b.getUuid()));
            } else {
                backupDAO.findByNetwork(network.getUuid()).forEach(b -> backupDAO.delete(b.getUuid()));
            }
        } catch (Exception e) {
            log.warn("delete("+uuid+"): error deleting backups: "+e);
        }
        final CloudService netStorage = cloudDAO.findByUuid(network.getStorage());
        if (netStorage == null) {
            log.warn("delete("+uuid+"): network storage not found: "+network.getStorage()+")");
        } else {
            try {
                netStorage.getStorageDriver(configuration).deleteNetwork(network.getUuid());
            } catch (IOException e) {
                log.error("delete("+uuid+"): error deleting network storage "+netStorage.getUuid()+": "+e);
            }
        }

        // delete nodes
        final List<BubbleNode> nodes = nodeDAO.findByNetwork(network.getUuid());
        if (force) {
            nodes.forEach(n -> nodeDAO.forceDelete(n.getUuid()));
        } else {
            nodes.forEach(n -> nodeDAO.delete(n.getUuid()));
        }

        // update or delete account plan
        final AccountPlan accountPlan = accountPlanDAO.findByAccountAndNetwork(network.getAccount(), network.getUuid());
        if (accountPlan == null) {
            log.warn("delete("+uuid+"): AccountPlan not found");
        } else {
            accountPlanDAO.update(accountPlan
                    .setEnabled(false)
                    .setNetwork(null)
                    .setDeletedNetwork(network.getUuid()));
        }

        // delete all other dependencies
        getConfiguration().deleteDependencies(network);

        if (force) {
            super.forceDelete(uuid);
        } else {
            super.delete(uuid);
        }
    }

}
