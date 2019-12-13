package bubble.cloud.storage.local;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class LocalStorageConfig {

    @Getter @Setter private String baseDir;

}
