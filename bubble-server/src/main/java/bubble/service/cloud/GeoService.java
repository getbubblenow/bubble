package bubble.service.cloud;

import bubble.cloud.CloudAndRegion;
import bubble.cloud.CloudRegion;
import bubble.cloud.CloudRegionRelative;
import bubble.cloud.CloudServiceType;
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
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static bubble.model.cloud.RegionalServiceDriver.findClosestRegions;
import static org.cobbzilla.util.collection.HasPriority.SORT_PRIORITY;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
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

    public GeoLocation locate (String accountUuid, String ip) {

        final List<CloudService> geoServices = cloudDAO.findByAccountAndType(accountUuid, CloudServiceType.geoLocation);
        if (geoServices.isEmpty()) throw new SimpleViolationException("err.geoLocateService.notFound");

        final List<GeoLocation> resolved = new ArrayList<>();
        geoServices.sort(SORT_PRIORITY);
        GeoCodeServiceDriver geoCodeDriver = null;
        for (CloudService geo : geoServices) {
            try {
                final GeoLocation result = geo.getGeoLocateDriver(configuration).geolocate(ip);
                if (result != null) {
                    if (!result.hasLatLon()) {
                        if (geoCodeDriver == null) {
                            final List<CloudService> geocodes = cloudDAO.findByAccountAndType(accountUuid, CloudServiceType.geoCode);
                            if (geocodes.isEmpty()) continue;
                            geocodes.sort(SORT_PRIORITY);
                            geoCodeDriver = geocodes.get(0).getGeoCodeDriver(configuration);
                        }
                        final GeoCodeResult code = geoCodeDriver.lookup(result);
                        result.setLat(code.getLat());
                        result.setLon(code.getLon());
                    }
                    resolved.add(result.setCloud(geo));
                }
            } catch (Exception e) {
                log.warn("locate: "+e, e);
            }
        }

        switch (resolved.size()){
            case 0: throw new SimpleViolationException("err.geoService.unresolvable", "could not resolve: "+ip, ip);

            // if we only have one, use that
            case 1: return resolved.get(0);

            // if we have two, pick the higher priority one
            case 2:
                return pickHighestPriority(geoServices, resolved);

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
                if (!near.isEmpty()) return pickHighestPriority(geoServices, near);

                // if there are none left, pick highest priority among all
                return pickHighestPriority(geoServices, resolved);
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

    public GeoTimeZone getTimeZone (Account account, String ip) {

        if (account == null) account = accountDAO.findFirstAdmin();
        final List<CloudService> geoServices = cloudDAO.findByAccountAndType(account.getUuid(), CloudServiceType.geoTime);
        if (geoServices.isEmpty()) throw new SimpleViolationException("err.geoTimeService.notFound");
        geoServices.sort(SORT_PRIORITY);

        final GeoLocation location = locate(account.getUuid(), ip);
        if (!location.hasLatLon()) {
            final List<CloudService> geocodes = cloudDAO.findByAccountAndType(account.getUuid(), CloudServiceType.geoCode);
            if (geocodes.isEmpty()) throw new SimpleViolationException("err.geoCodeService.notFound");
            geocodes.sort(SORT_PRIORITY);
            final GeoCodeResult code = geocodes.get(0).getGeoCodeDriver(configuration).lookup(location);
            location.setLat(code.getLat());
            location.setLon(code.getLon());
        }

        return geoServices.get(0).getGeoTimeDriver(configuration).getTimezone(location.getLat(), location.getLon());
    }

    public List<CloudRegionRelative> getCloudRegionRelatives(BubbleNetwork network, String userIp) {
        final GeoLocation geo = locate(network.getAccount(), userIp);
        final double latitude = geo.getLatitude();
        final double longitude = geo.getLongitude();

        // do we have a footprint?
        BubbleFootprint footprint = null;
        if (network.hasFootprint()) {
            footprint = footprintDAO.findByAccountAndId(network.getAccount(), network.getFootprint());
            if (footprint == null) throw notFoundEx(network.getFootprint());
        }

        // find all cloud services available to us
        final List<CloudService> clouds = cloudDAO.findByAccountAndType(network.getAccount(), CloudServiceType.compute);
        final List<CloudRegionRelative> closestRegions = findClosestRegions(clouds, footprint, latitude, longitude);
        if (closestRegions.isEmpty()) throw invalidEx("err.cloudRegions.required");
        return closestRegions;
    }

    public CloudAndRegion selectCloudAndRegion(BubbleNetwork network, NetLocation netLocation) {
        final CloudRegion closest;
        final CloudService cloud;
        if (netLocation.hasIp()) {
            // determine closest POP to userIp from cloud compute service
            final List<CloudRegionRelative> closestRegions = getCloudRegionRelatives(network, netLocation.getIp());
            closest = closestRegions.get(0);
            cloud = ((CloudRegionRelative) closest).getCloud();
            return new CloudAndRegion(cloud, closest);

        } else if (netLocation.hasCloud() && netLocation.hasRegion()) {
            // use explicitly provided cloud/region
            cloud = cloudDAO.findByAccountAndId(network.getAccount(), netLocation.getCloud());
            if (cloud == null) {
                log.error("selectCloudAndRegion (network="+network.getUuid()+"): netLocation.cloud="+netLocation.getCloud()+" not found under account="+network.getAccount());
                throw notFoundEx(netLocation.getCloud());
            }
            closest = cloud.getComputeDriver(configuration).getRegion(netLocation.getRegion());
            if (closest == null) throw notFoundEx(netLocation.getRegion());
            return new CloudAndRegion(cloud, closest);

        } else {
            return die("selectCloudAndRegion: no IP or region provided to launch first node");
        }
    }
}
