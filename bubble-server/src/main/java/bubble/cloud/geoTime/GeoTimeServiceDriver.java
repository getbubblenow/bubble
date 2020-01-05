package bubble.cloud.geoTime;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;

public interface GeoTimeServiceDriver extends CloudServiceDriver {

    // New York City, Eastern Standard Time
    String TEST_LONGITUDE = "-73.944";
    String TEST_LATITUDE = "40.661";
    String TEST_STANDARD_NAME = "Eastern Standard Time";
    String TEST_TIMEZONE_ID = "America/New York";

    @Override default CloudServiceType getType() { return CloudServiceType.geoTime; }

    GeoTimeZone getTimezone (String lat, String lon);

    @Override default boolean test () {
        final GeoTimeZone result = getTimezone(TEST_LATITUDE, TEST_LONGITUDE);
        return result != null
                && result.getStandardName().equals(TEST_STANDARD_NAME)
                && result.getTimeZoneId().equals(TEST_TIMEZONE_ID);
    }

}
