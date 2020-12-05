/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify;

import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleVersionInfo;
import bubble.model.cloud.NetLocation;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.service.upgrade.AppUpgradeService;
import bubble.service.boot.StandardSelfNodeService;
import bubble.service.cloud.StandardNetworkService;
import bubble.service.notify.NotificationService;
import bubble.service.upgrade.BubbleJarUpgradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static bubble.model.cloud.notify.NotificationType.peer_hello;
import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class NotificationHandler_hello_from_sage extends ReceivedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private BubblePlanDAO planDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private StandardNetworkService networkService;
    @Autowired private StandardSelfNodeService selfNodeService;
    @Autowired private BubbleJarUpgradeService jarUpgradeService;
    @Autowired private AppUpgradeService appUpgradeService;

    @Override public void handleNotification(ReceivedNotification n) {
        // Upstream is telling us about our peers
        final BubbleNode payloadNode = n.getNode();

        // First check to see if the sage reported a new jar version available
        if (payloadNode.hasSageVersion()) {
            final BubbleVersionInfo sageVersion = payloadNode.getSageVersion();
            log.info("handleNotification: sage version: "+sageVersion);
            if (configuration.setSageVersion(sageVersion)) {
                // run the jar upgrade service
                log.info("handleNotification: notifying jarUpgradeService: "+sageVersion);
                if (!jarUpgradeService.getIsAlive() && jarUpgradeService.shouldRun()) jarUpgradeService.startOrInterrupt();
            } else {
                // start the app upgrade service, if not running
                log.info("handleNotification: notifying appUpgradeService: "+sageVersion);
                if (!appUpgradeService.getIsAlive() && appUpgradeService.shouldRun()) appUpgradeService.startOrInterrupt();
            }
        }

        final BubbleNode thisNode = configuration.getThisNode();
        final List<BubbleNode> peers = payloadNode.getPeers();
        int peerCount = 0;
        boolean foundSelf = false;
        if (peers != null && !peers.isEmpty()) {
            for (BubbleNode peer : peers) {
                // skip ourselves
                if (peer.getFqdn().equals(thisNode.getFqdn())) {
                    foundSelf = true;
                    peerCount++;
                    continue;
                }

                // sanity checks
                if (!NotificationService.validPeer(thisNode, peer)) continue;

                final BubbleNode found = nodeDAO.findByUuid(peer.getUuid());
                if (found == null) {
                    log.info("hello_from_sage: creating peer: "+json(peer));
                    nodeDAO.create(peer);
                    notificationService.notify(peer, peer_hello, thisNode);
                } else {
                    found.upstreamUpdate(peer);
                    log.info("hello_from_sage: updating peer: "+json(peer));
                    nodeDAO.update(found);
                    notificationService.notify(found, peer_hello, thisNode);
                }
                peerCount++;
            }
        }

        // if we didn't see ourselves, bump peer count to avoid starting a node unnecessarily
        if (!foundSelf) peerCount++;

        // what's our plan?
        final AccountPlan accountPlan = accountPlanDAO.findByAccountAndNetwork(thisNode.getAccount(), thisNode.getNetwork());
        if (accountPlan == null) {
            log.warn("hello_from_sage: No account plan found for network: "+thisNode.getNetwork());
        } else {
            final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
            if (plan == null) {
                log.warn("hello_from_sage: No account plan found for accountPlan: "+accountPlan.getUuid());
            } else {
                if (peerCount < plan.getNodesIncluded() && selfNodeService.hasSageNode()) {
                    final BubbleNode sageNode = nodeDAO.findByUuid(selfNodeService.getSageNode().getUuid());
                    if (sageNode == null) {
                        log.warn("hello_from_sage: No sage node found: "+selfNodeService.getSageNode());
                    } else {
                        final BubbleNetwork network = networkDAO.findByUuid(thisNode.getNetwork());
                        // find the closest region that is not our current region
                        final NewNodeNotification newNodeRequest = new NewNodeNotification()
                                .setAccount(network.getAccount())
                                .setNetwork(network.getUuid())
                                .setNetworkName(network.getName())
                                .setDomain(network.getDomain())
                                .setNetLocation(NetLocation.fromCloudAndRegion(thisNode.getCloud(), thisNode.getRegion(), false))
                                .excludeCurrentRegion(thisNode)
                                .setAutomated(true);
                        log.info("hello_from_sage: requesting new node : " + json(newNodeRequest));
                        networkService.backgroundNewNode(newNodeRequest);
                    }
                }
            }
        }

    }
}
