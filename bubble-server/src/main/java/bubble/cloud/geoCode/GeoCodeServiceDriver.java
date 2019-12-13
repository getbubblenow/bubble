package bubble.cloud.geoCode;

import bubble.cloud.CloudServiceDriver;
import bubble.cloud.CloudServiceType;
import bubble.cloud.geoLocation.GeoLocation;

public interface GeoCodeServiceDriver extends CloudServiceDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.geoCode; }

    GeoCodeResult lookup (GeoLocation location);

}
