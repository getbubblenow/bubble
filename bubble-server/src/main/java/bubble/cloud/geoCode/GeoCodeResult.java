package bubble.cloud.geoCode;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.big;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class GeoCodeResult {

    @Getter @Setter private String lat;
    @Getter @Setter private String lon;

    @JsonIgnore @Transient public double getLatitude () { return big(lat).doubleValue(); }
    @JsonIgnore @Transient public double getLongitude () { return big(lon).doubleValue(); }

    public boolean valid () {
        try {
            Double.parseDouble(lat);
            Double.parseDouble(lon);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
