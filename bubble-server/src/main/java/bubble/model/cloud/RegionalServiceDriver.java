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

import static bubble.cloud.CloudRegionRelative.SORT_DISTANCE_THEN_NAME;
import static bubble.cloud.geoLocation.GeoLocation.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

public interface RegionalServiceDriver {

    Logger log = LoggerFactory.getLogger(RegionalServiceDriver.class);

    static List<CloudRegionRelative> findClosestRegions(BubbleConfiguration configuration,
                                                        List<CloudService> clouds,
                                                        BubbleFootprint footprint,
                                                        Double latitude,
                                                        Double longitude) {
        return findClosestRegions(configuration, clouds, footprint, latitude, longitude, null, true);
    }

    static List<CloudRegionRelative> findClosestRegions(BubbleConfiguration configuration,
                                                        List<CloudService> clouds,
                                                        BubbleFootprint footprint,
                                                        Double latitude,
                                                        Double longitude,
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
                    if (exclude != null && exclude.contains(new CloudAndRegion(c, region))) {
                        log.info("findClosestRegions: skipping excluded region: "+region);
                        continue;
                    }
                    if (!region.hasLocation() || region.getLocation() == NULL_LOCATION) {
                        // region has no location, it will always match with a distance of zero
                        addRegionWithUnknownDistance(allRegions, c, region);
                        continue;
                    }
                    if (latitude == null || longitude == null) {
                        // region has a location, we can never match with invalid coordinates
                        addRegionWithUnknownDistance(allRegions, c, region);
                        continue;
                    }
                    if (footprint != null && region.hasLocation() && !footprint.isAllowedCountry(region.getLocation().getCountry())) {
                        continue;
                    }
                    final CloudRegionRelative r = new CloudRegionRelative(region);
                    r.setCloud(c.getUuid());
                    if (latLonIsValid) {
                        r.setDistance(latitude, longitude);
                    } else {
                        r.setDistance(DEFAULT_GEO_LOCATION.getLatitude(), DEFAULT_GEO_LOCATION.getLongitude());
                    }
                    allRegions.add(r);
                }
            }
        }
        allRegions.sort(SORT_DISTANCE_THEN_NAME);
        return allRegions;
    }

    private static void addRegionWithUnknownDistance(List<CloudRegionRelative> allRegions,
                                                     CloudService c,
                                                     CloudRegion region) {
        final CloudRegionRelative r = new CloudRegionRelative(region);
        r.setDistance(null);
        r.setCloud(c.getUuid());
        allRegions.add(r);
    }

    List<CloudRegion> getRegions();

    default List<CloudRegion> getRegions(BubbleFootprint footprint) {
        return getRegions().stream()
                .filter(r -> footprint == null || footprint.isAllowedCountry(r.getLocation().getCountry()))
                .collect(Collectors.toList());
    }

    CloudRegion getRegion(String region);

}
