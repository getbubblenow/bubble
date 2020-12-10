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
import bubble.server.BubbleConfiguration;
import bubble.service.cloud.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static bubble.ApiConstants.*;
import static bubble.model.cloud.RegionalServiceDriver.findClosestRegions;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_NOT_FOUND;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class CloudServiceRegionsResource {

    private final Account account;

    public CloudServiceRegionsResource(Account account) { this.account = account; }

    @Autowired private BubbleConfiguration configuration;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleFootprintDAO footprintDAO;
    @Autowired private GeoService geoService;

    @GET
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="List cloud regions",
            description="List cloud regions. If the `footprint` param is provided, then only regions within that footprint will be returned",
            parameters=@Parameter(name="footprint", description="UUID or name of a BubbleFootprint to match"),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="a JSON array of CloudRegion objects"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="if footprint param was present and does not refer to a valid BubbleFootprint")
            }
    )
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
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="List nearest cloud regions",
            description="List nearest cloud regions, using geo-location services. If the `footprint` param is provided, then only regions within that footprint will be returned",
            parameters=@Parameter(name="footprint", description="UUID or name of a BubbleFootprint to match"),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="a JSON array of CloudRegionRelative objects, sorted by nearest first"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="if footprint param was present and does not refer to a valid BubbleFootprint")
            }
    )
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

        return ok(findClosestRegions(configuration, computeClouds(), footprint,
                loc.getLatitude(), loc.getLongitude(),
                null, geoService.supportsGeoLocation()));
    }

    public List<CloudRegion> findRegions(List<CloudService> clouds, BubbleFootprint footprint) {
        final List<CloudRegion> regions = new ArrayList<>();
        for (CloudService cloud : clouds) {
            try {
                for (CloudRegion region : cloud.getComputeDriver(configuration).getRegions(footprint)) {
                    regions.add(region.setCloud(cloud.getUuid()));
                }
            } catch (Exception e) {
                log.error("findRegions: error finding regions for cloud: "+cloud.getName()+"/"+cloud.getUuid()+": "+shortError(e), e);
            }
        }
        return regions;
    }

    public List<CloudService> computeClouds() {
        return cloudDAO.findByAccountAndType(account.getUuid(), CloudServiceType.compute);
    }

}
