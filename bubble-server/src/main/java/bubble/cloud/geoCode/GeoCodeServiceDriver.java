/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.geoCode;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;
import bubble.cloud.geoLocation.GeoLocation;

public interface GeoCodeServiceDriver extends CloudServiceDriver {

    GeoLocation TEST_LOCATION = new GeoLocation()
            .setCity("New York")
            .setRegion("NY")
            .setCountry("US");

    @Override default CloudServiceType getType() { return CloudServiceType.geoCode; }

    GeoCodeResult lookup (GeoLocation location);

    @Override default boolean test () {
        final GeoCodeResult result = lookup(TEST_LOCATION);
        return result != null && result.valid();
    }

}
