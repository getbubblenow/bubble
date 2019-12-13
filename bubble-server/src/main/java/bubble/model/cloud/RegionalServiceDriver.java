package bubble.model.cloud;

import bubble.cloud.CloudRegion;
import bubble.cloud.CloudRegionRelative;

import java.util.ArrayList;
import java.util.List;

import static java.util.Comparator.comparingDouble;

public interface RegionalServiceDriver {

    static List<CloudRegionRelative> findClosestRegions(List<CloudService> clouds,
                                                        BubbleFootprint footprint,
                                                        double latitude,
                                                        double longitude) {

        final List<CloudRegionRelative> allRegions = new ArrayList<>();
        for (CloudService c : clouds) {
            final List<CloudRegion> regions = c.getRegionalDriver().getRegions();
            for (CloudRegion region : regions) {
                if (footprint != null && !footprint.isAllowedCountry(region.getLocation().getCountry())) {
                    continue;
                }
                final CloudRegionRelative r = new CloudRegionRelative(region);
                r.setCloud(c);
                r.setDistance(latitude, longitude);
                allRegions.add(r);
            }
        }
        allRegions.sort(comparingDouble(CloudRegionRelative::getDistance));
        return allRegions;
    }

    List<CloudRegion> getRegions();
    CloudRegion getRegion(String region);

}
