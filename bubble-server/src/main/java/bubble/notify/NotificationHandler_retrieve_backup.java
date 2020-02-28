/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify;

import bubble.dao.cloud.BubbleBackupDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleBackup;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.ReceivedNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.notify.NotificationType.backup_response;

@Slf4j
public class NotificationHandler_retrieve_backup extends ReceivedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleBackupDAO backupDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode node = nodeDAO.findByUuid(n.getFromNode());
        if (node == null) {
            log.warn("retrieve_backup: node not found: "+n.getFromNode());
            return;
        }

        final BubbleNode payloadNode = n.getNode();
        if (!payloadNode.getNetwork().equals(node.getNetwork())) {
            log.error("retrieve_backup: network for fromNode does not match network in payloadNode");
            return;
        }

        // todo: add backupDate or backupLabel to notification so we can restore from a different backup if desired
        final BubbleBackup backup = backupDAO.findNewestSuccessfulByNetwork(payloadNode.getNetwork());
        if (backup == null) {
            log.error("retrieve_backup: no successful backups found");
            return;
        }
        payloadNode.setBackup(backup);

        notificationService.notify(node, backup_response, payloadNode);
    }
}
