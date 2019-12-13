package bubble.notify.geoTime;

import bubble.notify.SynchronousNotification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class GeoTimeNotification extends SynchronousNotification {

    @Getter @Setter private String lat;
    @Getter @Setter private String lon;
    @Getter @Setter private String geoTimeService;

}

