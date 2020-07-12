/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud;

import bubble.model.cloud.CloudService;
import lombok.*;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true) @EqualsAndHashCode
public class CloudAndRegion {

    @Getter @Setter private CloudService cloud;
    @Getter @Setter private CloudRegion region;

    public CloudAndRegion (String cloudUuid, String regionInternalName) {
        final CloudService c = new CloudService();
        c.setUuid(cloudUuid);
        setCloud(c);
        final CloudRegion r = new CloudRegion();
        r.setInternalName(regionInternalName);
        setRegion(r);
    }

}
