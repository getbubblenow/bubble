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
import lombok.experimental.Accessors;
import org.cobbzilla.util.math.Haversine;

import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;

@NoArgsConstructor @Accessors(chain=true)
public class GeoLocation {

    @Getter @Setter private String country;
    public boolean hasCountry() { return !empty(country); }

    @Getter @Setter private String region;
    @Getter @Setter private String city;
    @Getter @Setter private String lat;
    @Getter @Setter private String lon;

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

    public double distance(double lat, double lon) {
        return Haversine.distance(
                big(getLat()).doubleValue(),
                lat,
                big(getLon()).doubleValue(),
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
            distance(0, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JsonIgnore @Transient @Getter(lazy=true) private final String cacheKey = hashKey();
}
