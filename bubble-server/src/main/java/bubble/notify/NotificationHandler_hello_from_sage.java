package bubble.notify;

import bubble.cloud.CloudRegionRelative;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.service.boot.StandardSelfNodeService;
import bubble.service.notify.NotificationService;
import bubble.service.cloud.GeoService;
import bubble.service.cloud.StandardNetworkService;
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
    @Autowired private GeoService geoService;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private StandardNetworkService networkService;
    @Autowired private StandardSelfNodeService selfNodeService;

    @Override public void handleNotification(ReceivedNotification n) {
        // Upstream is telling us about our peers
        final BubbleNode payloadNode = n.getNode();
        final BubbleNode thisNode = configuration.getThisNode();
        final String systemAccount = configuration.getThisNode().getAccount();
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
                    notificationService.notify(systemAccount, peer, peer_hello, thisNode);
                } else {
                    found.upstreamUpdate(peer);
                    log.info("hello_from_sage: updating peer: "+json(peer));
                    nodeDAO.update(found);
                    notificationService.notify(systemAccount, found, peer_hello, thisNode);
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
                        final List<CloudRegionRelative> closestRegions = geoService.getCloudRegionRelatives(network, thisNode.getIp4());
                        if (closestRegions.isEmpty()) {
                            log.warn("hello_from_sage: no regions found");
                        } else {
                            // find the closest region that is not our current region
                            CloudRegionRelative closestNotUs = null;
                            for (CloudRegionRelative r : closestRegions) {
                                if (r.getInternalName().equalsIgnoreCase(thisNode.getRegion()) || r.getName().equalsIgnoreCase(thisNode.getRegion())) {
                                    continue;
                                }
                                closestNotUs = r;
                                break;
                            }
                            if (closestNotUs == null) {
                                // there is only one region
                                closestNotUs = closestRegions.get(0);
                            }
                            final CloudService cloud = cloudDAO.findByUuid(closestNotUs.getCloud());
                            if (cloud == null) {
                                log.warn("hello_from_sage: cloud not found, cannot request new node: "+closestNotUs.getCloud());
                            } else {
                                final NewNodeNotification newNodeRequest = new NewNodeNotification()
                                        .setAccount(network.getAccount())
                                        .setNetwork(network.getUuid())
                                        .setNetworkName(network.getName())
                                        .setDomain(network.getDomain())
                                        .setCloud(cloud.getUuid())
                                        .setRegion(closestNotUs.getInternalName())
                                        .setAutomated(true);
                                log.info("hello_from_sage: requesting new node : " + json(newNodeRequest));
                                networkService.backgroundNewNode(newNodeRequest);
                            }
                        }
                    }
                }
            }
        }

    }
}
