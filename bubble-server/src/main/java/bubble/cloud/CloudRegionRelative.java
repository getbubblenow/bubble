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

import java.util.Comparator;

import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor @Accessors(chain=true) @OpenApiSchema
public class CloudRegionRelative extends CloudRegion {

    public CloudRegionRelative(CloudRegion region) { copy(this, region); }

    @Getter @Setter private Double distance;
    public boolean hasDistance () { return distance != null; }

    public void setDistance(double latitude, double longitude) {
        if (hasLocation()) distance = getLocation().distance(latitude, longitude);
    }

    public static final CloudRegionRelativeComparator SORT_DISTANCE_THEN_NAME = new CloudRegionRelativeComparator();

    public static class CloudRegionRelativeComparator implements Comparator<CloudRegionRelative> {
        @Override public int compare(CloudRegionRelative crr1, CloudRegionRelative crr2) {
            if (crr1.hasDistance() && crr2.hasDistance()) {
                final int diff = Double.compare(crr1.getDistance(), crr2.getDistance());
                if (diff != 0) return diff;
            } else if (crr1.hasDistance()) {
                return -1;
            } else if (crr2.hasDistance()) {
                return 1;
            }
            return crr1.getInternalName().compareTo(crr2.getInternalName());
        }
    }

}
