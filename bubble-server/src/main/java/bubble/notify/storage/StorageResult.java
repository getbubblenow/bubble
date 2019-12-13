package bubble.notify.storage;

import bubble.model.cloud.notify.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.errorString;
import static org.cobbzilla.util.json.JsonUtil.json;

@NoArgsConstructor @Accessors(chain=true)
public class StorageResult {

    @Getter @Setter private NotificationType type;
    @Getter @Setter private String key;
    @Getter @Setter private Boolean success;
    public boolean success() { return success != null && success; }

    @Getter @Setter private String error;
    @Getter @Setter private JsonNode data;

    public static StorageResult successful(StorageDriverNotification notification, NotificationType type) {
        return new StorageResult()
                .setSuccess(true)
                .setKey(notification.getKey())
                .setType(type);
    }

    public static StorageResult failed(StorageDriverNotification notification, NotificationType type, Exception e) {
        return new StorageResult()
                .setSuccess(false)
                .setError(errorString(e))
                .setKey(notification.getKey())
                .setType(type);
    }

    public <T> T getData(Class<T> clazz) { return json(getData(), clazz); }

}
