package bubble.notify.storage;

import bubble.model.cloud.CloudService;
import bubble.model.cloud.StorageMetadata;
import bubble.model.cloud.notify.ReceivedNotification;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class NotificationHandler_storage_driver_read_metadata extends NotificationHandler_storage_driver_result<StorageMetadata> {

    @Override protected StorageMetadata handle(ReceivedNotification n, StorageDriverNotification notification, CloudService storage) {
        return storage.getStorageDriver(configuration).readMetadata(n.getFromNode(), notification.getKey());
    }

    @Override protected JsonNode toData(StorageMetadata metadata) {
        return json(json(metadata != null ? metadata : StorageMetadata.EMPTY_METADATA), JsonNode.class);
    }


}
