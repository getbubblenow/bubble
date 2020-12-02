/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.geoCode;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.OpenApiSchema;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;

import javax.persistence.Transient;

import static org.cobbzilla.util.daemon.ZillaRuntime.big;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true) @OpenApiSchema
public class GeoCodeResult {

    @ECField(type=EntityFieldType.decimal) @Getter @Setter private String lat;
    @ECField(type=EntityFieldType.decimal) @Getter @Setter private String lon;

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
