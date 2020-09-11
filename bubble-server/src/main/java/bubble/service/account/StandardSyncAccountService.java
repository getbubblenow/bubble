/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.account;

import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNetworkState;
import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;
import bubble.service.notify.NotificationService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static bubble.model.cloud.notify.NotificationType.sync_account;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Service @Slf4j
public class StandardSyncAccountService implements SyncAccountService {

    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private NotificationService notificationService;
    @Autowired private BubbleConfiguration configuration;

    public void syncAccount(@NonNull final Account account) {
        sync(account, new SyncAccountNotification(account.getUuid(), account.getHashedPassword().getHashedPassword(),
                                                  null));
    }

    public void syncPolicy(@NonNull final Account account, @NonNull final AccountPolicy policy) {
        sync(account, new SyncAccountNotification(account.getUuid(), null, policy));
    }

    private void sync(@NonNull final Account account, @NonNull final SyncAccountNotification notification) {
        final BubbleNetwork thisNetwork = configuration.getThisNetwork();
        if (thisNetwork == null) {
            // should never happen
            log.warn("sync: thisNetwork was null, sync_account is impossible");
            return;
        }

        if (!account.sync()) {
            log.info("sync: account sync disabled for account: "+account.getName());
            return;
        }

        final AnsibleInstallType installType = thisNetwork.getInstallType();
        if (installType == AnsibleInstallType.sage) {
            // changing account on sage, notify all bubbles launched by user that have syncAccount == true

            // sync account: sage -> node(s)
            // Note that a non-admin person can change only his own account on sage node, while admin can change any
            // account. In both cases, the change should be synced through the nodes, so even for non-admin account, the
            // sync should be done here.

            for (BubbleNetwork network : networkDAO.findByAccount(account.getUuid())) {
                if (network.getState() != BubbleNetworkState.running) continue;
                if (!network.syncAccount()) continue;
                for (BubbleNode node : nodeDAO.findByNetwork(network.getUuid())) {
                    if (node.getUuid().equals(configuration.getThisNode().getUuid())) {
                        log.info("sync: not notifying self");
                        continue;
                    }
                    log.info("sync: sending sync_account notification from sage to node: "+node.id());
                    notificationService.notify(node, sync_account, notification);
                }
            }

        } else if (installType == AnsibleInstallType.node) {
            // sync account: node -> sage
            // On sage there's just a single account related to this node - the node's network owner's account. So only
            // that account should be synced in this case. It doesn't matter if somebody already changed this account
            // to be non-admin (TODO strange situation and maybe it should be tested if it is forbidden already)

            if (account.getUuid().equals(thisNetwork.getAccount())) {
                log.info("sync: not syncing non-owner account from node to sage");
                return;
            }

            if (!thisNetwork.syncAccount()) {
                log.info("sync: disabled for node, not sending sync_account notification");
                return;
            }

            // changing account on node, notify sage, which will then notify all bubbles launched by user that have
            // syncAccount == true
            log.info("sync: sending sync_account notification from node to sage: "+configuration.getSageNode());
            notificationService.notify(configuration.getSageNode(), sync_account, notification);

        } else {
            reportError("sync("+account.getEmail()+"/"+account.getUuid()+"): invalid installType: "+installType);
        }
    }

}
