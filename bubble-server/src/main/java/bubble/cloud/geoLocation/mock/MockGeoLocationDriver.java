/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.geoLocation.mock;

import bubble.cloud.config.CloudApiUrlConfig;
import bubble.cloud.geoLocation.GeoLocateServiceDriverBase;
import bubble.cloud.geoLocation.GeoLocation;

public class MockGeoLocationDriver extends GeoLocateServiceDriverBase<CloudApiUrlConfig> {

    public static final GeoLocation MOCK_LOCAION = new GeoLocation()
            .setLat("40.661").setLon("-73.944")
            .setCountry("US").setCity("New York");

    @Override protected GeoLocation _geolocate(String ip) {
        return MOCK_LOCAION.setCloud(cloud);
    }

}
