package bubble.cloud;

import bubble.cloud.geoLocation.GeoLocation;
import lombok.Getter;
import lombok.Setter;

public class CloudRegion {

    @Getter @Setter private String name;
    @Setter private String internalName;
    public String getInternalName () { return internalName != null ? internalName : name; }

    @Getter @Setter private String description;

    @Getter @Setter private GeoLocation location;

    @Getter @Setter private Double costFactor = 1.0d;

}
