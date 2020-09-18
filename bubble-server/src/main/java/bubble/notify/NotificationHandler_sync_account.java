/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.service.account.SyncAccountNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Slf4j
public class NotificationHandler_sync_account extends ReceivedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired private AccountPolicyDAO accountPolicyDAO;

    @Override
    public void handleNotification(ReceivedNotification n) {
        final var node = nodeDAO.findByUuid(n.getFromNode());
        if (node == null) {
            log.warn("sync_account: node not found: " + n.getFromNode());
            return;
        }

        final var notification = json(n.getPayloadJson(), SyncAccountNotification.class);

        final var localAccount = accountDAO.findByUuid(notification.getAccountUuid());
        if (localAccount == null) {
            reportError("sync_account: localAccount not found: " + notification.getAccountUuid());
            return;
        }
        if (!localAccount.sync()) {
            log.info("sync_account: localAccount " + localAccount.getName() + " has sync disabled, no sync done");
            return;
        }

        final var incomingHashedPassword = notification.getUpdatedHashedPassword();
        if (!empty(incomingHashedPassword)) {
            localAccount.getHashedPassword().setHashedPassword(incomingHashedPassword);
            // if we are a node, set skipSync so we don't get caught in an infinite loop
            // (the node would notify the sage, which would notify the node, ad infinitum)
            localAccount.setSkipSync(configuration.getThisNetwork().node());
            // update password, if we are a sage, this will notify all networks of password change
            accountDAO.update(localAccount);
        }

        final var incomingPolicy = notification.getUpdatedPolicy();
        if (!empty(incomingPolicy)) {
            final var localPolicy = accountPolicyDAO.findSingleByAccount(localAccount.getUuid());
            if (localPolicy == null) {
                reportError("sync_account: local AccountPolicy not found for account: " + localAccount.getUuid());
                return;
            }
            localPolicy.update(incomingPolicy);
            localPolicy.setAccountContactsJson(incomingPolicy.getAccountContactsJson());
            localPolicy.setSkipSync(configuration.getThisNetwork().node());
            accountPolicyDAO.update(localPolicy);
        }
    }
}
