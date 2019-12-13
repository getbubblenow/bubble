package bubble.notify;

import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import org.cobbzilla.util.json.JsonSerializableException;

import static org.cobbzilla.util.json.JsonUtil.json;

public abstract class DelegatedNotificationHandlerBase extends ReceivedNotificationHandlerBase {

    public void notifySender(NotificationType type, String notificationId, BubbleNode sender, Object result) {

        final SynchronousNotificationReply reply = new SynchronousNotificationReply()
                .setNotificationId(notificationId);

        if (result instanceof Throwable) {
            reply.setException(new JsonSerializableException((Throwable) result));
        } else {
            reply.setResponse(json(json(result), JsonNode.class));
        }

        notificationService.notify(configuration.getThisNode().getAccount(), sender, type, reply);
    }

}
