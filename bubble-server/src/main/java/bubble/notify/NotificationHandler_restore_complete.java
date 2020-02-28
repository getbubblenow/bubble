/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify;

import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNetworkState;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.ReceivedNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Slf4j
public class NotificationHandler_restore_complete extends ReceivedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleNetworkDAO networkDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode node = nodeDAO.findByUuid(n.getFromNode());
        if (node == null) {
            die("restore_complete: node not found: "+n.getFromNode());
        } else {
            final String networkUuid = node.getNetwork();
            final BubbleNetwork network = networkDAO.findByUuid(networkUuid);
            if (network == null) {
                die("restore_complete: network not found: "+networkUuid);
            } else if (network.getState() != BubbleNetworkState.restoring) {
                die("restore_complete: network not in 'restoring' state ("+network.getState()+"): "+networkUuid);
            } else {
                network.setState(BubbleNetworkState.running);
                networkDAO.update(network);
                log.info("restore_complete: restore successfully completed, network running: "+networkUuid);
            }
        }
    }

}
