/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.cloud;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeState;
import bubble.service.cloud.NetworkService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static bubble.ApiConstants.ROOT_NETWORK_UUID;
import static bubble.ApiConstants.newNodeHostname;
import static bubble.model.cloud.BubbleNodeState.running;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.hibernate.criterion.Restrictions.isNotNull;

@Repository @Slf4j
public class BubbleNodeDAO extends AccountOwnedEntityDAO<BubbleNode> {

    @Getter(lazy=true) private static final BubbleNode rootNode = initRootNode();
    private static BubbleNode initRootNode() {
        final BubbleNode n = new BubbleNode();
        n.setUuid(ROOT_NETWORK_UUID);
        return n.setNetwork(ROOT_NETWORK_UUID);
    }

    @Autowired private NetworkService networkService;
    @Autowired private BubbleNetworkDAO networkDAO;

    @Override protected String getNameField() { return "fqdn"; }

    @Override public BubbleNode findByUuid(String uuid) {
        if (ROOT_NETWORK_UUID.equals(uuid)) return getRootNode();
        return super.findByUuid(uuid);
    }

    @Override public Object preCreate(BubbleNode node) {
        if (node.getUuid().equals(ROOT_NETWORK_UUID)) throw invalidEx("err.uuid.invalid");
        final BubbleNetwork network = networkDAO.findByUuid(node.getNetwork());

        if (!node.hasHost()) node.setHost(newNodeHostname());
        node.setFqdn(node.getHost()+"."+network.getNetworkDomain());

        // check for duplicate fqdn. DB unique constraint works fine, but results in an unfriendly error.
        final BubbleNode found = findByFqdn(node.getFqdn());
        if (found != null) throw invalidEx("err.node.name.alreadyExists", "node already exists with fqdn "+node.getFqdn()+": "+found.getUuid(), node.getFqdn());

        return node;
    }

    public BubbleNode findByFqdn(String fqdn) { return findByUniqueField("fqdn", fqdn); }

    @Override public void delete(String uuid) {
        final BubbleNode node = findByUuid(uuid);
        if (node == null) return;
        if (node.isRunning() || networkService.isReachable(node)) {
            throw invalidEx("err.node.running", "Node must be stopped before deleting");
        }
        getConfiguration().deleteDependencies(node);
        super.delete(uuid);
    }

    @Override public void forceDelete(String uuid) {
        final BubbleNode node = findByUuid(uuid);
        if (node == null) return;
        try {
            if (node.isRunning() || networkService.isReachable(node)) {
                networkService.killNode(node, "forceDelete");
            }
        } catch (Exception e) {
            log.error("forceDelete: error checking/stopping node: "+node.getUuid()+": "+e);
        }
        super.forceDelete(uuid);
    }

    public List<BubbleNode> findPeersByNetwork(String network) {
        return findByFieldAndFieldIn("network", network, "state", BubbleNodeState.ACTIVE_STATES);
    }

    public List<BubbleNode> findByNetwork(String network) { return findByField("network", network); }

    public List<BubbleNode> findRunningByNetwork(String network) {
        return findByFields("network", network, "state", running);
    }

    public List<BubbleNode> findByAccountAndNetworkAndDomain(String accountUuid, String networkUuid, String domainUuid) {
        return findByFields("account", accountUuid, "network", networkUuid, "domain", domainUuid);
    }

    public List<BubbleNode> findByAccountAndNetwork(String accountUuid, String networkUuid) {
        return findByFields("account", accountUuid, "network", networkUuid);
    }

    public List<BubbleNode> findRunningByAccountAndNetwork(String accountUuid, String networkUuid) {
        return findByFields("account", accountUuid, "network", networkUuid, "state", running);
    }

    public List<BubbleNode> findByAccountAndDomain(String accountUuid, String domainUuid) {
        return findByFields("account", accountUuid, "domain", domainUuid);
    }

    public BubbleNode findRunningByIp(String remoteHost) {
        return findByUniqueFields("state", running, "ip4", remoteHost);
    }

    public List<BubbleNode> findWithIp4() { return list(criteria().add(isNotNull("ip4"))); }

    public BubbleNode findByIp4(String ip4) { return findByUniqueField("ip4", ip4); }

}
