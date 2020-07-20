/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.upgrade;

import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.DelegatedNotificationHandlerBase;
import bubble.service.upgrade.BubbleJarUpgradeService;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.notify.NotificationType.upgrade_response;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class NotificationHandler_upgrade_request extends DelegatedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleJarUpgradeService upgradeService;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) {
            die("sender not found: "+n.getFromNode());
        } else {
            final String key = upgradeService.registerNodeUpgrade(sender.getUuid());
            notifySender(upgrade_response, n.getNotificationId(), sender, key);
        }
    }

}
