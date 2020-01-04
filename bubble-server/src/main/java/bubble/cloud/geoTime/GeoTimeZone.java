package bubble.cloud.geoTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class GeoTimeZone {

    @Getter @Setter private String timeZoneId;
    @Getter @Setter private String standardName;
    @Getter @Setter private Long currentOffsetMs;

}
