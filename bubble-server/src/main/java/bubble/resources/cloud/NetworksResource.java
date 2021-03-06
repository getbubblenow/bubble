/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.cloud.CloudServiceType;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleFootprintDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleFootprint;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import bubble.resources.TagsResource;
import bubble.resources.account.AccountOwnedResource;
import bubble.service.boot.SelfNodeService;
import bubble.service.cloud.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

import static bubble.ApiConstants.*;
import static bubble.model.cloud.RegionalServiceDriver.findClosestRegions;
import static org.cobbzilla.util.daemon.ZillaRuntime.big;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@SuppressWarnings({"SpringJavaAutowiredMembersInspection", "RSReferenceInspection"}) @Slf4j
public class NetworksResource extends AccountOwnedResource<BubbleNetwork, BubbleNetworkDAO> {

    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleFootprintDAO footprintDAO;
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private GeoService geoService;
    @Autowired private SelfNodeService selfNodeService;

    private BubbleDomain domain;

    public NetworksResource(Account account) { super(account); }

    public NetworksResource(Account account, BubbleDomain domain) {
        super(account);
        this.domain = domain;
    }

    @Override protected List<BubbleNetwork> list(ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (caller.admin() && this.domain != null) {
            return getDao().findAllByDomain(this.domain.getUuid());
        } else {
            if (caller.admin()) {
                final MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
                if (Boolean.parseBoolean(params.getFirst("all"))) return super.list(ctx);
            }
            return super.list(ctx).stream()
                    .filter(BubbleNetwork::isNotRootNetwork)
                    .filter(net -> accountPlanDAO.isNotDeleted(net.getUuid()))
                    .collect(Collectors.toList());
        }
    }

    @Override protected BubbleNetwork find(ContainerRequest ctx, String id) {
        final BubbleNetwork network = super.find(ctx, id);
        if (network == null) {
            final Account caller = userPrincipal(ctx);
            if (caller.admin() && this.domain != null) {
                return getDao().findByDomainAndId(this.domain.getUuid(), id);
            }
        }
        return network;
    }

    @Override protected BubbleNetwork setReferences(ContainerRequest ctx, Account caller, BubbleNetwork network) {
        BubbleDomain domain = domainDAO.findByAccountAndId(caller.getUuid(), network.getDomain());
        if (domain == null) {
            // check parent
            if (caller.hasParent()) domain = domainDAO.findPublicTemplate(caller.getParent(), network.getDomain());
            if (domain == null) throw notFoundEx(network.getDomain());
        }
        if (this.domain != null && !domain.getUuid().equals(this.domain.getUuid())) {
            throw invalidEx("err.domain.invalid", "Network cannot be created in domain "+domain.getName());
        }
        BubbleFootprint footprint = null;
        if (network.hasFootprint()) {
            footprint = footprintDAO.findByAccountAndId(caller.getUuid(), network.getFootprint());
            if (footprint == null) {
                // check parent
                if (caller.hasParent()) footprint = footprintDAO.findPublicTemplate(caller.getParent(), network.getFootprint());
                if (footprint == null) throw notFoundEx(network.getFootprint());
            }
        }
        return network
                .setDomain(domain.getUuid())
                .setDomainName(domain.getName())
                .setFootprint(footprint == null ? null : footprint.getUuid());
    }

    @Override protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, BubbleNetwork request) {
        // create networks through plans
        return false;
    }

    @Override protected boolean canDelete(ContainerRequest ctx, Account caller, BubbleNetwork found) {
        // delete networks through plans
        return false;
    }

    @GET @Path("/{id}"+EP_CLOSEST)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Find nearest regions for cloud service",
            description="Find nearest regions. If lat/lon are specified, we find the nearest regions to that lat/lon, otherwise attempt to discern caller's location using geo-location.",
            parameters={
                    @Parameter(name="id", description="UUID or name of the network", required=true),
                    @Parameter(name="type", description="the CloudServiceType to search for. usually `compute`"),
                    @Parameter(name="lat", description="latitude. if specified, `lon` must also be provided"),
                    @Parameter(name="lon", description="longitude. if specified, `lat` must also be provided")
            },
            responses=@ApiResponse(responseCode=SC_OK, description="the updated DnsRecord object")
    )
    public Response findClosest(@Context Request req,
                                @Context ContainerRequest ctx,
                                @PathParam("id") String id,
                                @QueryParam("type") CloudServiceType type,
                                @QueryParam("lat") String lat,
                                @QueryParam("lon") String lon) {
        final Account caller = userPrincipal(ctx);
        final BubbleNetwork network = find(ctx, id);
        if (network == null) throw notFoundEx(id);

        final BubbleFootprint footprint;
        if (network.hasFootprint()) {
            footprint = footprintDAO.findByAccountAndId(caller.getUuid(), network.getFootprint());
        } else {
            footprint = null;
        }

        // where are we?
        double latitude, longitude;
        if (lat != null && lon != null) {
            try {
                latitude = big(lat).doubleValue();
                longitude = big(lon).doubleValue();
            } catch (Exception e) {
                return invalid("err.latlon.invalid", "lat/lon was invalid: "+e.getMessage(), lat+","+lon);
            }
            if (latitude < 0 || longitude < 0) return invalid("err.latlon.invalid");
        } else {
            // try to figure it out from the IP
            final String remoteHost = getRemoteHost(req);
            final GeoLocation geo = geoService.locate(caller.getUuid(), remoteHost);
            latitude = geo.getLatitude();
            longitude = geo.getLongitude();
        }

        // find all cloud services available to us
        final CloudServiceType csType = type != null ? type : CloudServiceType.compute;
        final List<CloudService> clouds = cloudDAO.findByAccountAndType(caller.getUuid(), csType);

        // find closest region
        return ok(findClosestRegions(configuration, clouds, footprint, latitude, longitude));
    }


    @Path("/{id}"+EP_TAGS)
    @Operation(hidden=true)
    public TagsResource getTags(@Context ContainerRequest ctx,
                                @PathParam("id") String id) {
        final BubbleNetwork network = find(ctx, id);
        if (network == null) throw notFoundEx(id);
        return configuration.subResource(TagsResource.class, network);
    }

    @Path("/{id}"+EP_NODES)
    public NodesResource getNodes(@Context ContainerRequest ctx,
                                  @PathParam("id") String id) {
        final BubbleNetwork network = find(ctx, id);
        if (network == null) throw notFoundEx(id);
        return configuration.subResource(NodesResource.class, account, network);
    }

    @Path("/{id}"+EP_ACTIONS)
    public NetworkActionsResource getActions(@Context ContainerRequest ctx,
                                             @PathParam("id") String id) {
        final BubbleNetwork network = find(ctx, id);
        if (network == null) throw notFoundEx(id);
        return configuration.subResource(NetworkActionsResource.class, account, network);
    }

    @Path("/{id}"+EP_STORAGE)
    public StorageResource getStorage(@Context ContainerRequest ctx,
                                      @PathParam("id") String id) {
        final BubbleNetwork network = find(ctx, id);
        if (network == null) throw notFoundEx(id);

        final CloudService cloud = cloudDAO.findByAccountAndId(account.getUuid(), network.getStorage());
        if (cloud == null) throw notFoundEx(network.getStorage());

        return configuration.subResource(StorageResource.class, account, cloud);
    }

    @Path("/{id}"+EP_DNS)
    public NetworkDnsResource getNetworkDns(@Context ContainerRequest ctx,
                                            @PathParam("id") String id) {
        final BubbleNetwork network = find(ctx, id);
        if (network == null) throw notFoundEx(id);
        final BubbleDomain domain = domainDAO.findByUuid(network.getDomain());
        if (domain == null) throw notFoundEx(network.getDomain());
        return configuration.subResource(NetworkDnsResource.class, account, domain, network);
    }

    @Path("/{id}"+EP_BACKUPS)
    public BackupsResource getBackups(@Context ContainerRequest ctx,
                                      @PathParam("id") String id) {
        final BubbleNetwork network = find(ctx, id);
        if (network == null) throw notFoundEx(id);
        return configuration.subResource(BackupsResource.class, account, network);
    }

    @Path("/{id}"+EP_LOGS)
    @NonNull public LogsResource getLogs(@NonNull @Context final ContainerRequest ctx,
                                         @NonNull @PathParam("id") String id) {
        // caller must be admin
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) throw forbiddenEx();

        final var network = find(ctx, id);
        if (network == null) throw notFoundEx(id);

        // only available for this (current) network
        if (!configuration.getThisNetwork().getUuid().equals(network.getUuid())) throw forbiddenEx();

        return configuration.subResource(LogsResource.class, caller);
    }
}
