/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.geoLocation;

import bubble.model.cloud.CloudService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.cobbzilla.util.math.Haversine;
import org.cobbzilla.util.reflect.OpenApiSchema;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;

import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor @Accessors(chain=true) @ToString(of={"lat", "lon"}) @OpenApiSchema
public class GeoLocation {

    public static final GeoLocation GEO_NEW_YORK = new GeoLocation().setLat("40.661").setLon("-73.944");
    public static final GeoLocation GEO_SINGAPORE = new GeoLocation().setLat("1.283333").setLon("103.833333");
    public static final GeoLocation GEO_LONDON = new GeoLocation().setLat("51.507222").setLon("-0.1275");
    public static final GeoLocation GEO_ATLANTA = new GeoLocation().setLat("33.755").setLon("-84.39");
    public static final GeoLocation GEO_CHICAGO = new GeoLocation().setLat("41.881944").setLon("-87.627778");

    public static final GeoLocation DEFAULT_GEO_LOCATION = GEO_ATLANTA;

    public static final GeoLocation NULL_LOCATION = new GeoLocation();

    public GeoLocation(String country) {
        copy(this, NULL_LOCATION);
        setCountry(country);
    }

    @Getter @Setter private String country;
    public boolean hasCountry() { return !empty(country); }

    @Getter @Setter private String region;
    @Getter @Setter private String city;
    @ECField(type=EntityFieldType.decimal) @Getter @Setter private String lat;
    @ECField(type=EntityFieldType.decimal) @Getter @Setter private String lon;

    @JsonIgnore @Transient public double getLatitude () { return big(lat).doubleValue(); }
    @JsonIgnore @Transient public double getLongitude () { return big(lon).doubleValue(); }

    public String hashKey() { return hashOf(country, region, city, lat, lon); }

    @JsonIgnore @Transient @Getter @Setter private CloudService cloud;

    public double distance(GeoLocation g2) {
        return Haversine.distance(
                big(getLat()).doubleValue(),
                big(g2.getLat()).doubleValue(),
                big(getLon()).doubleValue(),
                big(g2.getLon()).doubleValue());
    }

    public Double distance(double lat, double lon) {
        if (lat < 0 || lon < 0) return null;
        double thisLat = big(getLat()).doubleValue();
        double thisLon = big(getLon()).doubleValue();
        if (thisLat < 0 || thisLon < 0) return null;
        return Haversine.distance(
                thisLat,
                lat,
                thisLon,
                lon);
    }

    @JsonIgnore public String getAddress() {
        final StringBuilder b = new StringBuilder();
        if (city != null) b.append(city);
        if (region != null) {
            if (b.length() > 0) b.append(", ");
            b.append(region);
        }
        if (country != null) {
            if (b.length() > 0) b.append(", ");
            b.append(country);
        }
        return b.toString();
    }

    public boolean hasLatLon() {
        try {
            return distance(0, 0) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @JsonIgnore @Transient @Getter(lazy=true) private final String cacheKey = hashKey();
}
