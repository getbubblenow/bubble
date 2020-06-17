/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.account;

import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.account.Account;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNetworkState;
import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;
import bubble.service.notify.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static bubble.model.cloud.notify.NotificationType.sync_password;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Service @Slf4j
public class StandardSyncPasswordService implements SyncPasswordService {

    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private NotificationService notificationService;
    @Autowired private BubbleConfiguration configuration;

    public void syncPassword(Account account) {
        final BubbleNetwork thisNetwork = configuration.getThisNetwork();
        if (thisNetwork == null) {
            // should never happen
            log.warn("syncPassword: thisNetwork was null, sync_password is impossible");
            return;
        }
        final AnsibleInstallType installType = thisNetwork.getInstallType();
        final SyncPasswordNotification notification = new SyncPasswordNotification(account);
        if (installType == AnsibleInstallType.sage) {
            // changing password on sage, notify all bubbles launched by user that have syncPassword == true

            for (BubbleNetwork network : networkDAO.findByAccount(account.getUuid())) {
                if (network.getState() != BubbleNetworkState.running) continue;
                if (!network.syncPassword()) continue;
                for (BubbleNode node : nodeDAO.findByNetwork(network.getUuid())) {
                    log.info("syncPassword: sending sync_password notification from sage to node: "+node.id());
                    notificationService.notify(node, sync_password, notification);
                }
            }

        } else if (installType == AnsibleInstallType.node) {
            if (!thisNetwork.syncPassword()) {
                log.info("syncPassword: disabled for node, not sending sync_password notification");
                return;
            }
            // changing password on node, notify sage, which will then notify all bubbles launched by user that have syncPassword == true
            log.info("syncPassword: sending sync_password notification from node to sage: "+configuration.getSageNode());
            notificationService.notify(configuration.getSageNode(), sync_password, notification);

        } else {
            reportError("syncPassword("+account.getName()+"/"+account.getUuid()+"): invalid installType: "+installType);
        }
    }

}
