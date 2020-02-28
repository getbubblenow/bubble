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

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class NotificationHandler_register_backup extends ReceivedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleBackupDAO backupDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode node = nodeDAO.findByUuid(n.getFromNode());
        if (node == null) {
            log.warn("register_backup: node not found: "+n.getFromNode());
        } else {
            final BubbleNode payloadNode = n.getNode();
            final BubbleBackup backup = payloadNode.getBackup();

            // ensure payload is correct
            validateMessage(node, payloadNode, backup);

            // do we already have this backup?
            final BubbleBackup existing = backupDAO.findByNetworkAndPath(backup.getNetwork(), backup.getPath());
            if (existing != null) {
                log.warn("register_backup: Backup already registered: "+existing.getUuid());
                return;
            }

            // create backup
            log.info("register_backup: registering backup: "+json(backup));
            backupDAO.create(backup);
            log.info("register_backup: backup successfully registered: "+json(backup));
        }
    }

    protected void validateMessage(BubbleNode node, BubbleNode payloadNode, BubbleBackup backup) {
        if (!node.getUuid().equals(payloadNode.getUuid())) {
            die("node UUID mismatch");
        }
        if (!node.getNetwork().equals(payloadNode.getNetwork())) {
            die("network UUID mismatch");
        }
        if (!node.getNetwork().equals(backup.getNetwork())) {
            die("network UUID mismatch in backup");
        }
        if (!backup.success()) {
            die("cannot register backup that did not succeed");
        }
    }

}
