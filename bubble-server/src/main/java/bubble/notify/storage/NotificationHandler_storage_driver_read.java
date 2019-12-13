package bubble.notify.storage;

import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import lombok.extern.slf4j.Slf4j;

import static bubble.model.cloud.notify.NotificationType.storage_driver_response;

@Slf4j
public class NotificationHandler_storage_driver_read extends NotificationHandler_storage_driver {

    @Override protected void handleNotification(ReceivedNotification n,
                                                BubbleNode sender,
                                                StorageDriverNotification notification,
                                                CloudService storage) {
        log.info("handleNotification: registering read for key="+notification.getKey());
        final String token = storageStreamService.registerRead(new StorageStreamRequest()
                .setCloud(storage.getUuid())
                .setFromNode(n.getFromNode())
                .setKey(notification.getKey()));
        notifySender(storage_driver_response, n.getNotificationId(), sender, token);
    }

}
