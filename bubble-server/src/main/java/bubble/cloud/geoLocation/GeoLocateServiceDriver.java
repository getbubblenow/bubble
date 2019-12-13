package bubble.cloud.geoLocation;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;

public interface GeoLocateServiceDriver extends CloudServiceDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.geoLocation; }

    GeoLocation geolocate(String ip);

}
