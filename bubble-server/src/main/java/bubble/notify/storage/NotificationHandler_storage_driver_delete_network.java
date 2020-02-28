/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.storage;

import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class NotificationHandler_storage_driver_delete_network extends NotificationHandler_storage_driver_result<Boolean> {

    @Override protected Boolean handle(ReceivedNotification n, StorageDriverNotification notification, CloudService storage) throws IOException {
        return storage.getStorageDriver(configuration).deleteNetwork(notification.getKey());
    }

    @Override protected JsonNode toData(Boolean returnVal) { return BooleanNode.valueOf(returnVal); }

}
