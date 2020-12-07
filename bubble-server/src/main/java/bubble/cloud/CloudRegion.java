/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud;

import bubble.cloud.geoLocation.GeoLocation;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.cobbzilla.util.reflect.OpenApiSchema;

import static java.util.UUID.randomUUID;

@Accessors(chain=true) @OpenApiSchema
@EqualsAndHashCode(of={"cloud", "internalName"})
@ToString(of={"cloud", "name", "internalName"})
public class CloudRegion {

    public static final CloudRegion[] EMPTY_REGIONS = new CloudRegion[0];

    @Getter @Setter private String uuid = randomUUID().toString();

    @Getter @Setter private String cloud;

    @Getter @Setter private Long id;
    @Getter @Setter private String name;
    @Setter private String internalName;
    public String getInternalName () { return internalName != null ? internalName : name; }

    @Getter @Setter private String description;

    @Getter @Setter private GeoLocation location;
    public boolean hasLocation () { return location != null; }

}
