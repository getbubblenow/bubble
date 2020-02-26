/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud;

import bubble.cloud.geoLocation.GeoLocation;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static java.util.UUID.randomUUID;

@Accessors(chain=true)
public class CloudRegion {

    @Getter @Setter private String uuid = randomUUID().toString();

    @Getter @Setter private String cloud;

    @Getter @Setter private String name;
    @Setter private String internalName;
    public String getInternalName () { return internalName != null ? internalName : name; }

    @Getter @Setter private String description;

    @Getter @Setter private GeoLocation location;

    @Getter @Setter private Double costFactor = 1.0d;

}
