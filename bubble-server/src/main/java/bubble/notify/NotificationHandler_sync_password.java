/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify;

import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.account.Account;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.service.account.SyncPasswordNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Slf4j
public class NotificationHandler_sync_password extends ReceivedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private AccountDAO accountDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode node = nodeDAO.findByUuid(n.getFromNode());
        if (node == null) {
            log.warn("sync_password: node not found: "+n.getFromNode());
        } else {
            final SyncPasswordNotification notification = json(n.getPayloadJson(), SyncPasswordNotification.class);

            final Account account = accountDAO.findByUuid(notification.getAccountUuid());
            if (account == null) {
                reportError("sync_password: account not found: "+notification.getAccountUuid());
                return;
            }
            if (!account.syncPassword()) {
                log.info("sync_password: account "+account.getName()+" has syncPassword disabled, not synchronizing");
                return;
            }

            account.getHashedPassword().setHashedPassword(notification.getHashedPassword());

            // if we are a node, set skipSyncPassword so we don't get caught in an infinite loop
            // (the node would notify the sage, which would notify the node, ad infinitum)
            if (configuration.getThisNetwork().getInstallType() == AnsibleInstallType.node) {
                account.setSkipSyncPassword(true);
            }

            // update password, if we are a sage, this will notify all networks of password change
            accountDAO.update(account);
        }
    }

}
