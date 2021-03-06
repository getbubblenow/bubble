/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.notify;

import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.notify.ReceivedNotificationDAO;
import bubble.model.cloud.notify.NotificationProcessingStatus;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.ReceivedNotificationHandler;
import bubble.notify.SynchronousNotification;
import bubble.notify.SynchronousNotificationReply;
import bubble.server.BubbleConfiguration;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static org.cobbzilla.util.json.JsonUtil.json;

@AllArgsConstructor @Slf4j
public class NotificationInboxProcessor implements Runnable {

    private final ReceivedNotification n;
    private final Map<String, SynchronousNotification> syncRequests;
    private final BubbleConfiguration configuration;
    private final ReceivedNotificationDAO receivedNotificationDAO;

    @Override public void run() {
        try {
            log.info("processing " + n.getType());
            processNotification(n, syncRequests);
            n.setProcessingStatus(NotificationProcessingStatus.completed);
            receivedNotificationDAO.update(n);
        } catch (Exception e) {
            log.error("error: " + e, e);
            n.setProcessingStatus(NotificationProcessingStatus.error);
            n.setException(e);
            receivedNotificationDAO.update(n);
        }
    }

    private void processNotification(ReceivedNotification n, Map<String, SynchronousNotification> syncRequests) {
        processNotification(n, syncRequests, configuration);
    }

    public static void processNotification(ReceivedNotification n,
                                           Map<String, SynchronousNotification> syncRequests,
                                           BubbleConfiguration configuration) {
        final ReceivedNotificationHandler handler = n.getType().getHandler(configuration);
        if (n.getType().isResponse()) {
            final SynchronousNotificationReply reply = json(n.getPayloadJson(), SynchronousNotificationReply.class);
            final SynchronousNotification syncNotification = syncRequests.get(reply.getNotificationId());
            if (syncNotification != null) {
                syncNotification.setResponse(reply.getResponse());
                syncNotification.setException(reply.getException());
            } else {
                log.warn("processNotification: reply for non-existent syncNotification: "+reply.getNotificationId());
                return;
            }
        }
        if (configuration.getBean(BubbleNodeDAO.class).findByUuid(n.getFromNode()) == null) {
            log.warn("processNotification: fromNode does not exist: "+n.getFromNode());
            return;
        }
        handler.handleNotification(n);
    }

}
