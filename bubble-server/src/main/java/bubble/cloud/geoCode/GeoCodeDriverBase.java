/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.geoCode;

import bubble.cloud.CloudServiceDriverBase;
import bubble.cloud.geoLocation.GeoLocation;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

@Slf4j
public abstract class GeoCodeDriverBase<T> extends CloudServiceDriverBase<T> implements GeoCodeServiceDriver {

    public static final long CACHE_TTL = TimeUnit.DAYS.toSeconds(20);
    public static final long ERROR_TTL = TimeUnit.SECONDS.toSeconds(20);

    private static class GeoCodeResultError extends GeoCodeResult {
        public GeoCodeResultError(Exception e) {
            setErrorClass(e.getClass().getName());
            setErrorMessage(e.getMessage());
        }
        @Getter @Setter private String errorClass;
        @Getter @Setter private String errorMessage;
    }


    @Autowired private RedisService redis;

    @Getter(lazy=true) private final RedisService cache = redis.prefixNamespace(getClass().getName()+"_cache_");

    @Override public GeoCodeResult lookup(GeoLocation location) {
        final String key = location.hashKey();
        String val = getCache().get(key);
        long ttl = CACHE_TTL;
        if (val == null) {
            GeoCodeResult r;
            try {
                r = _lookup(location);
            } catch (Exception e) {
                log.warn("_lookup: "+e, e);
                r = new GeoCodeResultError(e);
                ttl = ERROR_TTL;
            }
            val = json(r);
            getCache().set(key, val, EX, ttl);
        }
        return valOrError(val, GeoCodeResult.class);
    }

    protected abstract GeoCodeResult _lookup (GeoLocation location);

}
