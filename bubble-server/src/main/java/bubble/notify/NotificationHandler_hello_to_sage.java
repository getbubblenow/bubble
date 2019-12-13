package bubble.notify;

import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.ReceivedNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static bubble.model.cloud.notify.NotificationType.hello_from_sage;
import static java.util.stream.Collectors.joining;

@Slf4j
public class NotificationHandler_hello_to_sage extends ReceivedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode node = nodeDAO.findByUuid(n.getFromNode());
        if (node == null) {
            log.warn("hello_to_sage: node not found: "+n.getFromNode());
        } else {
            final BubbleNode payloadNode = n.getNode();
            node.upstreamUpdate(payloadNode);
            nodeDAO.update(node);
            final List<BubbleNode> peers = nodeDAO.findRunningByNetwork(node.getNetwork());
            log.info("hello_to_sage: returning peers: "+peers.stream().map(BubbleNode::getFqdn).collect(joining(", ")));
            node.setPeers(peers);

            final String systemAccount = configuration.getThisNode().getAccount();
            notificationService.notify(systemAccount, node, hello_from_sage, node);
        }
    }
}
