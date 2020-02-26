/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify.storage;

import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotificationHandler_storage_driver_exists extends NotificationHandler_storage_driver_result<Boolean> {

    @Override protected Boolean handle(ReceivedNotification n, StorageDriverNotification notification, CloudService storage) {
        return storage.getStorageDriver(configuration).exists(n.getFromNode(), notification.getKey());
    }

    @Override protected JsonNode toData(Boolean returnVal) { return BooleanNode.valueOf(returnVal); }

}
