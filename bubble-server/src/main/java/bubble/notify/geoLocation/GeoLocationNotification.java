package bubble.notify.geoLocation;

import bubble.notify.SynchronousNotification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class GeoLocationNotification extends SynchronousNotification {

    @Getter @Setter private String ip;
    @Getter @Setter private String geolocationService;

}
