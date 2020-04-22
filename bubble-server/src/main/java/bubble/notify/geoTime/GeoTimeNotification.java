/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.geoTime;

import bubble.notify.SynchronousNotification;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class GeoTimeNotification extends SynchronousNotification {

    @Getter @Setter private String lat;
    @Getter @Setter private String lon;
    @Getter @Setter private String geoTimeService;

    @JsonIgnore @Transient @Getter(lazy=true) private final String cacheKey
            = hashOf(lat, lon, geoTimeService);
}

