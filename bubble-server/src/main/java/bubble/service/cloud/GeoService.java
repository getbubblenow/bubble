/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.cloud.CloudAndRegion;
import bubble.cloud.CloudRegion;
import bubble.cloud.CloudRegionRelative;
import bubble.cloud.CloudServiceType;
import bubble.cloud.compute.ComputeServiceDriver;
import bubble.cloud.geoCode.GeoCodeResult;
import bubble.cloud.geoCode.GeoCodeServiceDriver;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.cloud.geoTime.GeoTimeZone;
import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.BubbleFootprintDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleFootprint;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.NetLocation;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.cobbzilla.util.cache.CacheLookupException;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static bubble.cloud.geoLocation.GeoLocation.NULL_LOCATION;
import static bubble.model.cloud.RegionalServiceDriver.findClosestRegions;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.DaemonThreadFactory.fixedPool;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.LocaleUtil.getDefaultLocales;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Service @Slf4j
public class GeoService {

    // todo: move to config?
    public static final int LOC_MAX_DISTANCE = 50000;

    @Autowired private AccountDAO accountDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleFootprintDAO footprintDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private RedisService redis;

    public boolean supportsGeoLocation () { return cloudDAO.adminHasType(CloudServiceType.geoLocation); }
    public boolean supportsGeoCode () { return cloudDAO.adminHasType(CloudServiceType.geoCode); }
    public boolean supportsGeoTime () { return cloudDAO.adminHasType(CloudServiceType.geoTime); }

    private static final long REDIS_CACHE_TIME = DAYS.toSeconds(1);
    private static final long MEMORY_CACHE_TIME = MINUTES.toSeconds(20);

    @Getter(lazy=true) private final RedisService locateRedis = redis.prefixNamespace(getClass().getName()+".locate");
    private final Map<String, GeoLocation> locateCache = new ExpirationMap<>(MEMORY_CACHE_TIME);

    public GeoLocation locate (String accountUuid, String ip) {
        return locate(accountUuid, ip, false);
    }

    private final Map<String, Future<GeoLocation>> backgroundLookups = new ConcurrentHashMap<>();
    private final ExecutorService backgroundLookupExec = fixedPool(5, "GeoService.backgroundLookupExec");

    public GeoLocation locate (String accountUuid, String ip, boolean cacheOnly) {
        if (!supportsGeoLocation()) {
            log.warn("locate: no geoLocation cloud services defined, returning null location");
            return NULL_LOCATION;
        }
        final String cacheKey = hashOf(accountUuid, ip);
        return locateCache.computeIfAbsent(cacheKey, k -> {
            final String found = getLocateRedis().get(cacheKey);
            if (found != null) return json(found, GeoLocation.class);

            if (cacheOnly) {
                backgroundLookups.computeIfAbsent(cacheKey, key -> backgroundLookupExec.submit(() -> {
                    try {
                        return getLocation(accountUuid, ip, cacheKey);
                    } finally {
                        backgroundLookups.remove(key);
                    }
                }));
                throw new CacheLookupException(ip);
            }

            return getLocation(accountUuid, ip, cacheKey);
        });
    }

    private GeoLocation getLocation(String accountUuid, String ip, String cacheKey) {
        if (!supportsGeoLocation()) {
            log.warn("getLocation: no geoLocation cloud services defined, returning null location");
            return NULL_LOCATION;
        }
        List<CloudService> geoLocationServices = null;
        if (accountUuid != null) {
            geoLocationServices = cloudDAO.findByAccountAndType(accountUuid, CloudServiceType.geoLocation);
        }
        if (empty(geoLocationServices)) {
            // try to find using admin
            final Account admin = accountDAO.getFirstAdmin();
            if (admin != null && !admin.getUuid().equals(accountUuid)) {
                geoLocationServices = cloudDAO.findByAccountAndType(admin.getUuid(), CloudServiceType.geoLocation);
            }
        }
        if (empty(geoLocationServices)) {
            throw invalidEx("err.geoLocateService.notFound");
        }

        log.info("locate: resolving IP: "+ip+" for cacheKey: "+cacheKey);
        final List<GeoLocation> resolved = new ArrayList<>();
        GeoCodeServiceDriver geoCodeDriver = null;
        for (CloudService geo : geoLocationServices) {
            try {
                final GeoLocation result = geo.getGeoLocateDriver(configuration).geolocate(ip);
                if (result != null) {
                    if (!result.hasLatLon() && supportsGeoCode()) {
                        if (geoCodeDriver == null) {
                            List<CloudService> geoCodeServices = null;
                            if (accountUuid != null) {
                                geoCodeServices = cloudDAO.findByAccountAndType(accountUuid, CloudServiceType.geoCode);
                            }
                            if (empty(geoCodeServices)) {
                                final Account admin = accountDAO.getFirstAdmin();
                                if (admin != null && !admin.getUuid().equals(accountUuid)) {
                                    geoCodeServices = cloudDAO.findByAccountAndType(admin.getUuid(), CloudServiceType.geoCode);
                                }
                            }
                            if (empty(geoCodeServices)) {
                                throw invalidEx("err.geoCodeService.notFound");
                            }
                            geoCodeDriver = geoCodeServices.get(0).getGeoCodeDriver(configuration);
                        }
                        final GeoCodeResult code = geoCodeDriver.lookup(result);
                        if (code == null) {
                            log.info("locate: driver lookup returned null result, skipping");
                            continue;
                        }
                        result.setLat(code.getLat());
                        result.setLon(code.getLon());
                    }
                    resolved.add(result.setCloud(geo));
                }
            } catch (Exception e) {
                log.warn("locate: "+e, e);
            }
        }

        final GeoLocation geoLocation = getGeoLocation(ip, geoLocationServices, resolved);
        getLocateRedis().set(cacheKey, json(geoLocation), EX, REDIS_CACHE_TIME);

        return geoLocation;
    }

    public GeoLocation getGeoLocation(String ip, List<CloudService> geoLocationServices, List<GeoLocation> resolved) {
        switch (resolved.size()) {
            case 0: throw invalidEx("err.geoService.unresolvable", "could not resolve: "+ip, ip);

            // if we only have one, use that
            case 1: return resolved.get(0);

            // if we have two, pick the higher priority one
            case 2:
                return pickHighestPriority(geoLocationServices, resolved);

            default:
                // determine the average lat/lon
                final double averageLat = resolved.stream().mapToDouble(GeoLocation::getLatitude).sum() / ((double) resolved.size());
                final double averageLon = resolved.stream().mapToDouble(GeoLocation::getLongitude).sum() / ((double) resolved.size());

                // throw out any that are more than 50km off the average
                final List<GeoLocation> near = new ArrayList<>();
                for (GeoLocation loc : resolved) {
                    if (loc.distance(averageLat, averageLon) <= LOC_MAX_DISTANCE) {
                        near.add(loc);
                    }
                }
                // if there are any left, pick highest priorty among those remaining
                if (!near.isEmpty()) return pickHighestPriority(geoLocationServices, near);

                // if there are none left, pick highest priority among all
                return pickHighestPriority(geoLocationServices, resolved);
        }
    }

    public GeoLocation pickHighestPriority(List<CloudService> geoServices, List<GeoLocation> resolved) {
        for (CloudService geo : geoServices) {
            final Optional<GeoLocation> found = resolved.stream().filter(g -> g.getCloud().getUuid().equals(geo.getUuid())).findAny();
            if (found.isPresent()) return found.get();
        }
        // we should never get here
        return resolved.get(0);
    }

    @Getter(lazy=true) private final RedisService timezoneRedis = redis.prefixNamespace(getClass().getName()+".timezone");
    private final Map<String, GeoTimeZone> timezoneCache = new ExpirationMap<>(MEMORY_CACHE_TIME);

    public GeoTimeZone getTimeZone (final Account account, String ip) {
        if (!supportsGeoTime()) {
            log.warn("getTimeZone: no geoTime clouds configured, returning UTC time zone");
            return GeoTimeZone.UTC;
        }
        final AtomicReference<Account> acct = new AtomicReference<>(account);
        return timezoneCache.computeIfAbsent(ip, k -> {
            final String found = getTimezoneRedis().get(ip);
            if (found != null) return json(found, GeoTimeZone.class);

            if (acct.get() == null) acct.set(accountDAO.getFirstAdmin());
            if (acct.get() == null) throw invalidEx("err.activation.required");
            List<CloudService> geoServices = cloudDAO.findByAccountAndType(acct.get().getUuid(), CloudServiceType.geoTime);
            if (geoServices.isEmpty() && !account.admin()) {
                // try to find using admin
                final Account admin = accountDAO.getFirstAdmin();
                if (admin != null && !admin.getUuid().equals(account.getUuid())) {
                    geoServices = cloudDAO.findByAccountAndType(admin.getUuid(), CloudServiceType.geoTime);
                }
            }
            if (geoServices.isEmpty()) {
                throw invalidEx("err.geoTimeService.notFound");
            }

            final GeoLocation location = locate(acct.get().getUuid(), ip);
            if (!location.hasLatLon()) {
                final List<CloudService> geocodes = cloudDAO.findByAccountAndType(acct.get().getUuid(), CloudServiceType.geoCode);
                if (geocodes.isEmpty()) throw invalidEx("err.geoCodeService.notFound");
                final GeoCodeResult code = geocodes.get(0).getGeoCodeDriver(configuration).lookup(location);
                location.setLat(code.getLat());
                location.setLon(code.getLon());
            }

            final GeoTimeZone timezone = geoServices.get(0).getGeoTimeDriver(configuration).getTimezone(location.getLat(), location.getLon());
            getTimezoneRedis().set(ip, json(timezone), EX, REDIS_CACHE_TIME);
            return timezone;
        });
    }

    public List<CloudRegionRelative> getCloudRegionRelatives(BubbleNetwork network, String userIp) {
        return getCloudRegionRelatives(network, userIp, null);
    }

    public List<CloudRegionRelative> getCloudRegionRelatives(BubbleNetwork network,
                                                             String userIp,
                                                             Collection<CloudAndRegion> exclude) {
        if (supportsGeoLocation()) {
            final GeoLocation geo = locate(network.getAccount(), userIp);
            final double latitude = geo.getLatitude();
            final double longitude = geo.getLongitude();
            return getCloudRegionRelatives(network, latitude, longitude, exclude);
        } else {
            return getCloudRegionRelatives(network, 0.0, 0.0, exclude);
        }
    }

    public List<CloudRegionRelative> getCloudRegionRelatives(BubbleNetwork network,
                                                             double latitude,
                                                             double longitude,
                                                             Collection<CloudAndRegion> exclude) {
        // do we have a footprint?
        BubbleFootprint footprint = null;
        if (network.hasFootprint()) {
            footprint = footprintDAO.findByAccountAndId(network.getAccount(), network.getFootprint());
            if (footprint == null) throw notFoundEx(network.getFootprint());
        }

        // find all cloud services available to us
        final List<CloudService> clouds = cloudDAO.findByAccountAndType(network.getAccount(), CloudServiceType.compute);
        final List<CloudRegionRelative> closestRegions = findClosestRegions(configuration, clouds, footprint, latitude, longitude, exclude, supportsGeoLocation());
        if (closestRegions.isEmpty()) throw invalidEx("err.cloudRegions.required");
        return closestRegions;
    }

    public CloudAndRegion selectCloudAndRegion(BubbleNetwork network, NetLocation netLocation) {
        return selectCloudAndRegion(network, netLocation, Collections.emptyList());
    }

    public CloudAndRegion selectCloudAndRegion(BubbleNetwork network,
                                               NetLocation netLocation,
                                               Collection<CloudAndRegion> exclude) {
        final CloudRegion closest;
        final String cloudUuid;
        if (netLocation.hasIp()) {
            // determine closest POP to userIp from cloud compute service
            final List<CloudRegionRelative> closestRegions = getCloudRegionRelatives(network, netLocation.getIp(), exclude);
            closest = closestRegions.get(0);
            cloudUuid = closest.getCloud();
            final CloudService cloud = cloudDAO.findByUuid(cloudUuid);
            if (cloud == null) throw notFoundEx(cloudUuid);
            return new CloudAndRegion(cloud, closest);

        } else if (netLocation.hasCloud() && netLocation.hasRegion()) {
            // use explicitly provided cloud/region
            final CloudService cloud = cloudDAO.findByAccountAndId(network.getAccount(), netLocation.getCloud());
            if (cloud == null) {
                log.error("selectCloudAndRegion (network="+network.getUuid()+"): netLocation.cloud="+netLocation.getCloud()+" not found under account="+network.getAccount());
                throw notFoundEx(netLocation.getCloud());
            }
            final ComputeServiceDriver computeDriver = cloud.getComputeDriver(configuration);
            final CloudRegion desiredRegion = computeDriver.getRegion(netLocation.getRegion());
            if (desiredRegion == null) throw notFoundEx(netLocation.getRegion());

            final GeoLocation desiredLocation = desiredRegion.hasLocation() ? desiredRegion.getLocation() : NULL_LOCATION;
            final List<CloudRegionRelative> candidateRegions = getCloudRegionRelatives(network, desiredLocation.getLatitude(), desiredLocation.getLongitude(), exclude);

            if (candidateRegions.isEmpty()) return die("selectCloudAndRegion: no candidate regions found, desiredRegion="+desiredRegion);

            // If multiple candidate regions have distance zero, choose the one that is in the same cloud
            final CloudRegionRelative preciseRegion = candidateRegions.stream()
                    .filter(r -> r.getDistance() == 0)
                    .filter(r -> r.getCloud().equals(netLocation.getCloud()))
                    .filter(r -> !netLocation.exactRegion() || r.getInternalName().equals(netLocation.getRegion()))
                    .findFirst()
                    .orElse(null);

            if (preciseRegion != null) return new CloudAndRegion(cloud, preciseRegion);
            if (netLocation.exactRegion()) {
                log.error("selectCloudAndRegion: exactRegion was set, and region was not a candidate: "+netLocation.getRegion());
                throw notFoundEx(netLocation.getRegion());
            }

            // No precise region available, use the closest
            return new CloudAndRegion(cloud, candidateRegions.get(0));

        } else {
            return die("selectCloudAndRegion: no IP or region provided to launch first node");
        }
    }

    private final Map<String, String> firstLocaleCache = new ExpirationMap<>();

    public String getFirstLocale(Account account, String remoteHost, String langHeader) {
        return firstLocaleCache.computeIfAbsent(hashOf(account.getUuid(), remoteHost, langHeader), k -> {
            final List<String> supportedLocales = getSupportedLocales(account, remoteHost, langHeader);
            return empty(supportedLocales) ? null : supportedLocales.get(0);
        });
    }

    private final Map<String, List<String>> localesCache = new ExpirationMap<>(DAYS.toMillis(1));

    public List<String> getSupportedLocales(Account caller, String remoteHost, String langHeader) {
        return localesCache.computeIfAbsent((caller==null?"null":caller.getUuid())+remoteHost+"\t"+langHeader, k -> {
            final List<String> locales = new ArrayList<>();
            final String[] allLocales = configuration.getAllLocales();
            if (langHeader != null) locales.add(langHeader);

            try {
                final GeoLocation loc = locate(caller == null ? null : caller.getUuid(), remoteHost);
                if (loc != null) {
                    final List<String> found = getDefaultLocales(loc.getCountry());
                    for (int i=0; i<found.size(); i++) {
                        if (!locales.contains(found.get(i))) {
                            locales.add(found.get(i));
                        }
                    }
                }
            } catch (SimpleViolationException e) {
                throw e;

            } catch (Exception e) {
                log.warn("detectLocale: "+e);
            }

            // filter out any locales that are not supported
            final List<String> supportedLocales = locales.stream()
                    .filter(loc -> ArrayUtils.contains(allLocales, loc))
                    .collect(Collectors.toList());
            if (supportedLocales.isEmpty()) {
                // re-add default locale if nothing else is supported
                supportedLocales.add(configuration.getDefaultLocale());
            }
            return supportedLocales;
        });
    }

}
