/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.reflect.OpenApiSchema;

import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor @Accessors(chain=true) @OpenApiSchema
public class CloudRegionRelative extends CloudRegion {

    public CloudRegionRelative(CloudRegion region) { copy(this, region); }

    @Getter @Setter private double distance;

    public void setDistance(double latitude, double longitude) {
        if (hasLocation()) distance = getLocation().distance(latitude, longitude);
    }

}
