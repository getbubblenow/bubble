/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import bubble.model.account.message.ActionTarget;
import bubble.model.bill.AccountPlan;
import bubble.model.cloud.*;
import bubble.server.BubbleConfiguration;
import bubble.service.account.StandardAuthenticatorService;
import bubble.service.cloud.NodeLaunchMonitor;
import bubble.service.cloud.NodeProgressMeterTick;
import bubble.service.cloud.StandardNetworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;

import static bubble.ApiConstants.*;
import static bubble.model.cloud.BubbleNetwork.TAG_ALLOW_REGISTRATION;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_FORBIDDEN;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class NetworkActionsResource {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private StandardNetworkService networkService;
    @Autowired private NodeLaunchMonitor launchMonitor;
    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private StandardAuthenticatorService authenticatorService;

    private final Account account;
    private final BubbleNetwork network;

    public NetworkActionsResource (Account account, BubbleNetwork network) {
        this.account = account;
        this.network = network;
    }

    @POST @Path(EP_START)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Launch a Bubble",
            description="Launch a Bubble. If cloud and region are provided, then the Bubble will be launched in that region of that cloud. If neither are provided, then we'll try to select the nearest region. Returns a NewNodeNotification containing info that can be used to track launch status.",
            parameters={
                    @Parameter(name="cloud", description="UUID or name of a CloudService whose type is `compute`. Optional, but if specified then `region` must also be supplied."),
                    @Parameter(name="region", description="Name of a region within the cloud. Optional, but if specified then `cloud` must also be supplied."),
                    @Parameter(name="exactRegion", description="If true and cloud and region are also supplied, then fail if the Bubble cannot be launched in the specified region. Otherwise, a relaunch in the next-closest region will be attempted")
            },
            responses=@ApiResponse(responseCode=SC_OK, description="a NewNodeNotification object")
    )
    public Response startNetwork(@Context Request req,
                                 @Context ContainerRequest ctx,
                                 @QueryParam("cloud") String cloud,
                                 @QueryParam("region") String region,
                                 @QueryParam("exactRegion") Boolean exactRegion) {

        final Account caller = userPrincipal(ctx);
        if (!authAccount(caller)) return forbidden();

        // any nodes?
        final List<BubbleNode> nodes = nodeDAO.findByNetwork(network.getUuid());
        if (!nodes.isEmpty()) {
            if (nodes.stream().anyMatch(BubbleNode::isRunning)) {
                return invalid("err.network.alreadyStarted");
            }
        }

        if (!network.getState().canStart()) return invalid("err.network.cannotStartInCurrentState");
        authenticatorService.ensureAuthenticated(ctx, ActionTarget.network);

        return _startNetwork(network, cloud, region, exactRegion, req);
    }

    @GET @Path(EP_STATUS)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="List launch statuses",
            description="List launch statuses. Returns an array of NodeProgressMeterTick objects, each representing the latest status update from a launching Bubble. Normally only one Bubble is launching at a time, so there will only be one element in the array.",
            responses=@ApiResponse(responseCode=SC_OK, description="array of NodeProgressMeterTick objects")
    )
    public Response listLaunchStatuses(@Context Request req,
                                       @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        final String account = caller.admin() ? network.getAccount() : caller.getUuid();
        return ok(launchMonitor.listLaunchStatuses(account, network.getUuid()));
    }

    @GET @Path(EP_STATUS+"/{uuid}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Get launch status for a specific launch",
            description="Get launch status for a specific launch. Returns a NodeProgressMeterTick object representing the latest status update from the launching Bubble.",
            parameters=@Parameter(name="uuid", description="UUID of the NewNodeNotification returned when the Bubble was launched", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="a NodeProgressMeterTick object")
    )
    public Response requestLaunchStatus(@Context Request req,
                                        @Context ContainerRequest ctx,
                                        @PathParam("uuid") String uuid) {
        final Account caller = userPrincipal(ctx);
        final String account = caller.admin() ? network.getAccount() : caller.getUuid();
        final NodeProgressMeterTick tick = launchMonitor.getLaunchStatus(account, uuid);
        return tick == null ? notFound(uuid) : ok(tick);
    }

    @Path(EP_KEYS)
    @NonNull public NetworkBackupKeysResource getBackupKeys(@NonNull @Context final ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) throw forbiddenEx();

        // must request from the network you are on
        if (!network.getUuid().equals(configuration.getThisNetwork().getUuid())) {
            throw invalidEx("err.networkKeys.mustRequestFromSameNetwork");
        }

        final AccountPolicy policy = policyDAO.findSingleByAccount(caller.getUuid());
        if (policy == null || !policy.hasVerifiedAccountContacts()) {
            throw invalidEx("err.networkKeys.noVerifiedContacts");
        }

        authenticatorService.ensureAuthenticated(ctx, ActionTarget.network);

        return configuration.subResource(NetworkBackupKeysResource.class, caller, network);
    }

    @POST @Path(EP_STOP)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Stop a Bubble",
            description="Stop a Bubble. The caller must own the Bubble or be an admin. Returns true",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="true"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="the caller is not authorized to stop the Bubble")
            }
    )
    public Response stopNetwork(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!authAccount(caller)) return forbidden();

        // any nodes?
        final List<BubbleNode> nodes = nodeDAO.findByNetwork(network.getUuid());
        if (nodes.isEmpty()) log.warn("stopNetwork: no nodes found for network: "+network.getUuid());

        authenticatorService.ensureAuthenticated(ctx, ActionTarget.network);

        return ok(networkService.stopNetwork(network));
    }

    @POST @Path(EP_RESTORE)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_CLOUDS, API_TAG_BACKUP_RESTORE},
            summary="Launch a Bubble in restore mode",
            description="Launch a Bubble in restore mode. If cloud and region are provided, then the Bubble will be launched in that region of that cloud. If neither are provided, then we'll try to select the nearest region. Returns a NewNodeNotification containing info that can be used to track launch status.",
            parameters={
                    @Parameter(name="cloud", description="UUID or name of a CloudService whose type is `compute`. Optional, but if specified then `region` must also be supplied."),
                    @Parameter(name="region", description="Name of a region within the cloud. Optional, but if specified then `cloud` must also be supplied."),
                    @Parameter(name="exactRegion", description="If true and cloud and region are also supplied, then fail if the Bubble cannot be launched in the specified region. Otherwise, a relaunch in the next-closest region will be attempted")
            },
            responses=@ApiResponse(responseCode=SC_OK, description="a NewNodeNotification object")
    )
    public Response restoreNetwork(@Context Request req,
                                   @Context ContainerRequest ctx,
                                   @QueryParam("cloud") String cloud,
                                   @QueryParam("region") String region,
                                   @QueryParam("exactRegion") Boolean exactRegion) {
        final Account caller = userPrincipal(ctx);
        if (!authAccount(caller)) return forbidden();

        authenticatorService.ensureAuthenticated(ctx, ActionTarget.network);

        return ok(networkService.restoreNetwork(network, cloud, region, exactRegion, req));
    }

    @PUT @Path(EP_FORK)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_CLOUDS,
            summary="Fork a Bubble",
            description="Fork a Bubble. Must be admin. Clones this Bubble's data onto another system, can be configured as either a sage or a node",
            responses=@ApiResponse(responseCode=SC_OK, description="a NewNodeNotification object")
    )
    public Response fork(@Context Request req,
                         @Context ContainerRequest ctx,
                         ForkRequest forkRequest) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();

        authenticatorService.ensureAuthenticated(ctx, ActionTarget.network);

        final String fqdn = forkRequest.getFqdn();
        final BubbleDomain domain = domainDAO.findByFqdn(fqdn);
        if (domain == null) return invalid("err.fqdn.domain.invalid", "domain not found for "+fqdn, fqdn);

        final ValidationResult errors = new ValidationResult();
        final String netName = domain.networkFromFqdn(fqdn, errors);
        if (errors.isInvalid()) throw invalidEx(errors);
        if (netName == null) throw invalidEx("err.fqdn.invalid");

        final BubbleNetwork network = networkDAO.findByAccountAndId(caller.getUuid(), netName);
        if (network == null) return invalid("err.fqdn.network.invalid", "network not found for "+fqdn, fqdn);

        // enable registration if current network allows it
        network.setTag(TAG_ALLOW_REGISTRATION, configuration.getThisNetwork().getBooleanTag(TAG_ALLOW_REGISTRATION, false));
        networkDAO.update(network);

        if (forkRequest.getCloud() == null) {
            forkRequest.setCloud(configuration.getThisNode().getCloud());
        }
        if (forkRequest.getRegion() == null) {
            // todo: choose region that is closest to the caller's IP, but is not the same region
            forkRequest.setRegion(configuration.getThisNode().getRegion());
        }

        final AccountPlan accountPlan = accountPlanDAO.findByAccountAndId(caller.getUuid(), network.getName());
        if (accountPlan == null) return invalid("err.fqdn.plan.invalid", "no plan found for network "+network.getName(), network.getName());

        network.setForkHost(network.hostFromFqdn(fqdn));
        network.setAdminEmail(forkRequest.getAdminEmail());

        return _startNetwork(network,
                forkRequest.getCloud(),
                forkRequest.getRegion(),
                forkRequest.getExactRegion(),
                req);
    }

    public Response _startNetwork(BubbleNetwork network,
                                  String cloud,
                                  String region,
                                  Boolean exactRegion,
                                  Request req) {
        if ((region != null && cloud == null) || (region == null && cloud != null)) {
            throw invalidEx("err.netlocation.invalid", "must specify both cloud and region, or neither");
        }

        final NetLocation netLocation = (region != null)
                ? NetLocation.fromCloudAndRegion(cloud, region, exactRegion)
                : NetLocation.fromIp(getRemoteHost(req));

        return ok(networkService.startNetwork(network, netLocation));
    }

    public boolean authAccount(Account caller) {
        // caller must be admin or owner
        return caller.admin() || caller.getUuid().equals(network.getAccount());
    }

}
