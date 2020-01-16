package bubble.notify.geoLocation;

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
public class GeoLocationNotification extends SynchronousNotification {

    @Getter @Setter private String ip;
    @Getter @Setter private String geolocationService;

    @JsonIgnore @Transient @Getter(lazy=true) private final String cacheKey
            = hashOf(ip, geolocationService);
}
