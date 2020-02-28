/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.geoCode;

import bubble.cloud.geoLocation.GeoLocation;
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
public class GeoCodeNotification extends SynchronousNotification {

    @Getter @Setter private GeoLocation location;
    @Getter @Setter private String geoCodeService;

    @JsonIgnore @Transient @Getter(lazy=true) private final String cacheKey
            = hashOf(location == null ? null : location.getCacheKey(), geoCodeService);
}
