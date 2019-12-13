package bubble.cloud.geoTime;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;

public interface GeoTimeServiceDriver extends CloudServiceDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.geoTime; }

    GeoTimeZone getTimezone (String lat, String lon);

}
