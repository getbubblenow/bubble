package bubble.notify;

import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleBackup;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.server.BubbleConfiguration;
import bubble.service.backup.RestoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class NotificationHandler_backup_response extends ReceivedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private RestoreService restoreService;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode node = nodeDAO.findByUuid(n.getFromNode());
        if (node == null) {
            log.warn("backup_response: node not found: "+n.getFromNode());
            return;
        }

        final BubbleNode selfNode = configuration.getThisNode();
        final BubbleNode payloadNode = n.getNode();
        if (!payloadNode.getNetwork().equals(selfNode.getNetwork())) {
            log.error("backup_response: network for selfNode does not match network in payloadNode");
            return;
        }

        final BubbleBackup backup = payloadNode.getBackup();
        if (backup == null) {
            log.error("backup_response: no backup found in payload");
            return;
        }

        restoreService.restore(payloadNode.getRestoreKey(), backup);
    }
}
