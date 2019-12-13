package bubble.notify.geoCode;

import bubble.cloud.geoLocation.GeoLocation;
import bubble.notify.SynchronousNotification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class GeoCodeNotification extends SynchronousNotification {

    @Getter @Setter private GeoLocation location;
    @Getter @Setter private String geoCodeService;

}
