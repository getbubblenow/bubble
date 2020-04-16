/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.cloud.CloudRegion;
import bubble.cloud.CloudServiceType;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.dao.cloud.BubbleFootprintDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleFootprint;
import bubble.model.cloud.CloudService;
import bubble.service.cloud.GeoService;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static bubble.ApiConstants.EP_CLOSEST;
import static bubble.ApiConstants.getRemoteHost;
import static bubble.model.cloud.RegionalServiceDriver.findClosestRegions;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class CloudServiceRegionsResource {

    private Account account;

    public CloudServiceRegionsResource(Account account) { this.account = account; }

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleFootprintDAO footprintDAO;
    @Autowired private GeoService geoService;

    @GET
    public Response listRegions(@Context Request req,
                                @Context ContainerRequest ctx,
                                @QueryParam("footprint") String footprintId) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(account.getUuid())) return forbidden();

        final BubbleFootprint footprint;
        if (empty(footprintId)) {
            footprint = null;
        } else {
            footprint = footprintDAO.findByAccountAndId(caller.getUuid(), footprintId);
            if (footprint == null) return notFound(footprintId);
        }
        return ok(findRegions(computeClouds(), footprint));
    }

    @GET @Path(EP_CLOSEST)
    public Response listClosestRegions(@Context Request req,
                                       @Context ContainerRequest ctx,
                                       @QueryParam("footprint") String footprintId) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(account.getUuid())) return forbidden();

        final String remoteHost = getRemoteHost(req);
        final GeoLocation loc = geoService.locate(caller.getUuid(), remoteHost);

        final BubbleFootprint footprint;
        if (footprintId == null) {
            footprint = null;
        } else {
            footprint = footprintDAO.findByAccountAndId(account.getUuid(), footprintId);
            if (footprint == null) return notFound(footprintId);
        }

        return ok(findClosestRegions(computeClouds(), footprint, loc.getLatitude(), loc.getLongitude()));
    }

    public List<CloudRegion> findRegions(List<CloudService> clouds, BubbleFootprint footprint) {
        final List<CloudRegion> regions = new ArrayList<>();
        for (CloudService cloud : clouds) {
            for (CloudRegion region : cloud.getRegionalDriver().getRegions(footprint)) {
                regions.add(region.setCloud(cloud.getUuid()));
            }
        }
        return regions;
    }

    public List<CloudService> computeClouds() {
        return cloudDAO.findByAccountAndType(account.getUuid(), CloudServiceType.compute);
    }

}
