/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import bubble.cloud.CloudRegion;
import lombok.*;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true) @EqualsAndHashCode(of={"id", "regions"}) @ToString
public class PackerImage {

    @Getter @Setter private String id;
    @Getter @Setter private String name;
    @Getter @Setter private CloudRegion[] regions;

}
