package bubble.cloud.geoTime;

import bubble.cloud.CloudServiceDriverBase;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

@Slf4j
public abstract class GeoTimeServiceDriverBase<T> extends CloudServiceDriverBase<T> implements GeoTimeServiceDriver {

    public static final long CACHE_TTL = TimeUnit.DAYS.toSeconds(20);
    public static final long ERROR_TTL = TimeUnit.SECONDS.toSeconds(20);

    private static class GeoTimeZoneError extends GeoTimeZone {
        public GeoTimeZoneError(Exception e) {
            setErrorClass(e.getClass().getName());
            setErrorMessage(e.getMessage());
        }
        @Getter @Setter private String errorClass;
        @Getter @Setter private String errorMessage;
    }


    @Autowired private RedisService redis;

    @Getter(lazy=true) private final RedisService cache = redis.prefixNamespace(getClass().getName()+"_cache_");

    @Override public GeoTimeZone getTimezone(String lat, String lon) {
        final String key = lat+":"+lon;
        String val = getCache().get(key);
        long ttl = CACHE_TTL;
        if (val == null) {
            GeoTimeZone tz;
            try {
                tz = _getTimezone(lat, lon);
            } catch (Exception e) {
                log.warn("_lookup: "+e, e);
                tz = new GeoTimeZoneError(e);
                ttl = ERROR_TTL;
            }
            val = json(tz);
            getCache().set(key, val, EX, ttl);
        }
        return valOrError(val, GeoTimeZone.class);
    }

    protected abstract GeoTimeZone _getTimezone(String lat, String lon);
}
