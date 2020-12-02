/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.geoTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.reflect.OpenApiSchema;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true) @OpenApiSchema
public class GeoTimeZone {

    public static final GeoTimeZone UTC = new GeoTimeZone("Etc/UTC", "UTC", 0L);

    @Getter @Setter private String timeZoneId;
    @Getter @Setter private String standardName;
    @Getter @Setter private Long currentOffsetMs;

}
