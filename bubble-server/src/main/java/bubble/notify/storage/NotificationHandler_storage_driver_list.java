package bubble.notify.storage;

import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class NotificationHandler_storage_driver_list extends NotificationHandler_storage_driver_result<StorageListing> {

    @Override protected StorageListing handle(ReceivedNotification n, StorageDriverNotification notification, CloudService storage) throws IOException {
        return storage.getStorageDriver(configuration).list(n.getFromNode(), notification.getKey());
    }

    @Override protected JsonNode toData(StorageListing returnVal) { return json(json(returnVal), JsonNode.class); }

}
