/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import bubble.cloud.CloudAndRegion;
import bubble.cloud.CloudRegion;
import bubble.cloud.CloudRegionRelative;
import bubble.server.BubbleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingDouble;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

public interface RegionalServiceDriver {

    Logger log = LoggerFactory.getLogger(RegionalServiceDriver.class);

    static List<CloudRegionRelative> findClosestRegions(BubbleConfiguration configuration,
                                                        List<CloudService> clouds,
                                                        BubbleFootprint footprint,
                                                        double latitude,
                                                        double longitude) {
        return findClosestRegions(configuration, clouds, footprint, latitude, longitude, null, true);
    }

    static List<CloudRegionRelative> findClosestRegions(BubbleConfiguration configuration,
                                                        List<CloudService> clouds,
                                                        BubbleFootprint footprint,
                                                        double latitude,
                                                        double longitude,
                                                        Collection<CloudAndRegion> exclude,
                                                        boolean latLonIsValid) {

        final List<CloudRegionRelative> allRegions = new ArrayList<>();
        for (CloudService c : clouds) {
            final List<CloudRegion> regions;
            try {
                regions = c.getComputeDriver(configuration).getRegions();
            } catch (Exception e) {
                log.warn("findClosestRegions: error fetching regions from "+c.getName()+"/"+c.getUuid()+": "+shortError(e), e);
                continue;
            }
            if (regions != null) {
                for (CloudRegion region : regions) {
                    if (footprint != null && !footprint.isAllowedCountry(region.getLocation().getCountry())) {
                        continue;
                    }
                    if (exclude != null && exclude.contains(new CloudAndRegion(c, region))) {
                        log.info("findClosestRegions: skipping excluded region: "+region);
                        continue;
                    }
                    final CloudRegionRelative r = new CloudRegionRelative(region);
                    r.setCloud(c.getUuid());
                    if (latLonIsValid) {
                        r.setDistance(latitude, longitude);
                    } else {
                        r.setDistance(-1);
                    }
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
