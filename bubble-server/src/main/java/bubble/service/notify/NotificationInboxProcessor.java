package bubble.service.notify;

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

    private ReceivedNotification n;
    private Map<String, SynchronousNotification> syncRequests;
    private BubbleConfiguration configuration;
    private ReceivedNotificationDAO receivedNotificationDAO;

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
        handler.handleNotification(n);
    }

}
