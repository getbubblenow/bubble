/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.geoTime;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;

public interface GeoTimeServiceDriver extends CloudServiceDriver {

    // New York City, Eastern Standard Time
    String TEST_LONGITUDE = "-73.944";
    String TEST_LATITUDE = "40.661";
    String TEST_STANDARD_NAME = "Eastern Standard Time";
    String TEST_DAYLIGHT_NAME = "Eastern Daylight Time";
    String TEST_TIMEZONE_ID = "America/New_York";

    @Override default CloudServiceType getType() { return CloudServiceType.geoTime; }

    GeoTimeZone getTimezone (String lat, String lon);

    @Override default boolean test () {
        final GeoTimeZone result = getTimezone(TEST_LATITUDE, TEST_LONGITUDE);
        return result != null
                && (result.getStandardName().equals(TEST_STANDARD_NAME) || result.getStandardName().equals(TEST_DAYLIGHT_NAME))
                && result.getTimeZoneId().equals(TEST_TIMEZONE_ID);
    }

}
