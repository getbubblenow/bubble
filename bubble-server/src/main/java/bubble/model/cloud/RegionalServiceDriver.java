/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.cloud;

import bubble.cloud.CloudRegion;
import bubble.cloud.CloudRegionRelative;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingDouble;

public interface RegionalServiceDriver {

    static List<CloudRegionRelative> findClosestRegions(List<CloudService> clouds,
                                                        BubbleFootprint footprint,
                                                        double latitude,
                                                        double longitude) {

        final List<CloudRegionRelative> allRegions = new ArrayList<>();
        for (CloudService c : clouds) {
            final List<CloudRegion> regions = c.getRegionalDriver().getRegions();
            if (regions != null) {
                for (CloudRegion region : regions) {
                    if (footprint != null && !footprint.isAllowedCountry(region.getLocation().getCountry())) {
                        continue;
                    }
                    final CloudRegionRelative r = new CloudRegionRelative(region);
                    r.setCloud(c.getUuid());
                    r.setDistance(latitude, longitude);
                    allRegions.add(r);
                }
            }
        }
        allRegions.sort(comparingDouble(CloudRegionRelative::getDistance));
        return allRegions;
    }

    List<CloudRegion> getRegions();

    default List<CloudRegion> getRegions(BubbleFootprint footprint) {
        return getRegions().stream()
                .filter(r -> footprint == null || footprint.isAllowedCountry(r.getLocation().getCountry()))
                .collect(Collectors.toList());
    }

    CloudRegion getRegion(String region);

}
