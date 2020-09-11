/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.device;

import bubble.cloud.geoLocation.GeoLocation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;

@AllArgsConstructor @Slf4j
public class FlexRouterProximityComparator implements Comparator<FlexRouterInfo> {

    private final GeoLocation geoLocation;
    private final String preferredIp;

    @Override public int compare(FlexRouterInfo r1, FlexRouterInfo r2) {

        // if preferred ip matches, that takes precedence over everything
        if (r1.getVpnIp().equals(preferredIp)) return Integer.MIN_VALUE;
        if (r2.getVpnIp().equals(preferredIp)) return Integer.MAX_VALUE;

        // if a router has no location info, it goes last
        if (r1.hasNoGeoLocation()) return Integer.MAX_VALUE;
        if (r2.hasNoGeoLocation()) return Integer.MIN_VALUE;

        // if WE have no location info, just compare ports (we choose randomly)
        if (geoLocation == null) return r1.getPort() - r2.getPort();

        // compare distances. if they are equals, just compare ports (we choose randomly)
        final double distance1 = r1.distance(geoLocation);
        final double distance2 = r2.distance(geoLocation);
        final int delta = (int) (1000.0d * (distance1 - distance2));
        return delta != 0 ? delta : r1.getPort() - r2.getPort();
    }

}
