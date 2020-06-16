package bubble.cloud.compute;

import bubble.cloud.CloudRegion;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true) @EqualsAndHashCode(of={"id", "regions"})
public class PackerImage {

    @Getter @Setter private String id;
    @Getter @Setter private String name;
    @Getter @Setter private CloudRegion[] regions;

}
