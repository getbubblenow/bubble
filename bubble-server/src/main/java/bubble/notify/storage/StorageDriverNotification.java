package bubble.notify.storage;

import bubble.model.cloud.StorageMetadata;
import bubble.notify.SynchronousNotification;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class StorageDriverNotification extends SynchronousNotification {

    @Getter @Setter private String storageService;
    @Getter @Setter private String key;
    @Getter @Setter private StorageMetadata metadata;
    @Getter @Setter private String token;

    public StorageDriverNotification (String key, StorageMetadata metadata) {
        this.key = key;
        this.metadata = metadata;
    }

    public StorageDriverNotification (String key) { this(key, null); }

}
