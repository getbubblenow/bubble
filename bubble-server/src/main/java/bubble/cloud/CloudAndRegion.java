package bubble.cloud;

import bubble.model.cloud.CloudService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class CloudAndRegion {

    @Getter @Setter private CloudService cloud;
    @Getter @Setter private CloudRegion region;

}
