/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.storage;

import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import static bubble.model.cloud.notify.NotificationType.storage_driver_response;

@Slf4j
public abstract class NotificationHandler_storage_driver_result<T> extends NotificationHandler_storage_driver {

    @Override protected void handleNotification(ReceivedNotification n,
                                               BubbleNode sender,
                                               StorageDriverNotification notification,
                                               CloudService storage) {
        StorageResult result;
        try {
            final T returnVal = handle(n, notification, storage);
            result = StorageResult.successful(notification, n.getType())
                    .setData(toData(returnVal));
        } catch (Exception e) {
            log.error("handleNotification: "+e);
            result = StorageResult.failed(notification, n.getType(), e);
        }
        notifySender(storage_driver_response, n.getNotificationId(), sender, result);
    }

    protected abstract T handle(ReceivedNotification n, StorageDriverNotification notification, CloudService storage) throws IOException;

    protected abstract JsonNode toData(T returnVal);

}
