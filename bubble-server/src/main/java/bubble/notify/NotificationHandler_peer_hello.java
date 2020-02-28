/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify;

import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.service.notify.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class NotificationHandler_peer_hello extends ReceivedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        // A peer said hello. That's nice.
        final BubbleNode payloadNode = n.getNode();
        final BubbleNode thisNode = configuration.getThisNode();
        final String systemAccount = configuration.getThisNode().getAccount();
        if (!NotificationService.validPeer(thisNode, payloadNode)) {
            log.warn("peer_hello: invalid peer: " + payloadNode.getUuid());
            return;
        }
        final BubbleNode found = nodeDAO.findByUuid(payloadNode.getUuid());
        if (found == null) {
            log.info("peer_hello: creating peer: "+json(payloadNode));
            nodeDAO.create(payloadNode);
        } else {
            found.upstreamUpdate(payloadNode);
            log.info("peer_hello: updating peer: "+json(found));
            nodeDAO.update(found);
        }
    }

}
