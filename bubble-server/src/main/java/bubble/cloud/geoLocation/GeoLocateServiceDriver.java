/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.geoLocation;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;

import static org.cobbzilla.util.network.NetworkUtil.getExternalIp;

public interface GeoLocateServiceDriver extends CloudServiceDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.geoLocation; }

    GeoLocation geolocate(String ip);

    @Override default boolean test () {
        final GeoLocation result = geolocate(getExternalIp());
        return result != null && result.hasCountry();
    }

}
