package bubble.cloud.geoLocation;

import bubble.cloud.CloudServiceDriverBase;
import bubble.dao.cloud.CloudServiceDataDAO;
import lombok.Cleanup;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.http.HttpMeta;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.io.Tarball;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

@Slf4j
public abstract class GeoLocateServiceDriverBase<T> extends CloudServiceDriverBase<T> implements GeoLocateServiceDriver {

    public static final long CACHE_TTL = TimeUnit.DAYS.toSeconds(20);
    public static final long ERROR_TTL = TimeUnit.SECONDS.toSeconds(20);

    private static class GeoLocationError extends GeoLocation {
        public GeoLocationError(Exception e) {
            setErrorClass(e.getClass().getName());
            setErrorMessage(e.getMessage());
        }
        @Getter @Setter private String errorClass;
        @Getter @Setter private String errorMessage;
    }

    @Autowired protected CloudServiceDataDAO cloudDataDAO;

    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService cache = redis.prefixNamespace(getClass().getName()+"_cache_");

    @Override public GeoLocation geolocate (String ip) {
        String val = getCache().get(ip);
        long ttl = CACHE_TTL;
        if (val == null) {
            GeoLocation loc;
            try {
                loc = _geolocate(ip);
            } catch (Exception e) {
                log.warn("_lookup: "+e, e);
                loc = new GeoLocationError(e);
                ttl = ERROR_TTL;
            }
            val = json(loc);
            getCache().set(ip, val, "EX", ttl);
        }
        return valOrError(val, GeoLocation.class);
    }

    protected abstract GeoLocation _geolocate (String ip);

    public File initFile(String url, String pathMatch, List<NameAndValue> headers) {
        try {
            final HttpRequestBean request = new HttpRequestBean(url).setHeaders(headers);
            final HttpMeta meta = HttpUtil.getHeadMetadata(request);

            final String uniq = sha256_hex(hashOf(url, headers));
            final String dbKey = "dbcache_" + uniq;
            final File dbFile = cloudDataDAO.getFile(cloud.getUuid(), dbKey);
            if (!meta.shouldRefresh(dbFile)) return dbFile; // we are current!

            final String key = "urlcache_" + uniq;
            final File tarball = cloudDataDAO.getFile(cloud.getUuid(), key);
            if (meta.shouldRefresh(tarball)) {
                HttpUtil.getResponse(request).toFile(tarball);
            }

            @Cleanup("delete") final TempDir tempDir = Tarball.unroll(tarball);
            final File found = findFile(tempDir, Pattern.compile(pathMatch));
            if (found == null) {
                return die("initFile: tarball " + abs(tarball) + " did not contain a file matching pattern: " + pathMatch);
            }
            copyFile(found, dbFile);
            return dbFile;

        } catch (Exception e) {
            return die("initFile: "+e.getMessage());
        }
    }

}
