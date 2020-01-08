package bubble.resources.cloud;

import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.account.message.AccountMessageDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.model.bill.AccountPlan;
import bubble.model.cloud.*;
import bubble.server.BubbleConfiguration;
import bubble.service.backup.NetworkKeysService;
import bubble.service.cloud.NodeProgressMeterTick;
import bubble.service.cloud.StandardNetworkService;
import lombok.extern.slf4j.Slf4j;
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
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class NetworkActionsResource {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private StandardNetworkService networkService;
    @Autowired private AccountMessageDAO messageDAO;
    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private NetworkKeysService keysService;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private BubbleConfiguration configuration;

    private Account account;
    private BubbleNetwork network;

    public NetworkActionsResource (Account account, BubbleNetwork network) {
        this.account = account;
        this.network = network;
    }

    @POST @Path(EP_START)
    public Response startNetwork(@Context Request req,
                                 @Context ContainerRequest ctx,
                                 @QueryParam("cloud") String cloud,
                                 @QueryParam("region") String region) {

        final Account caller = userPrincipal(ctx);
        if (!authAccount(caller)) return forbidden();

        // any nodes?
        final List<BubbleNode> nodes = nodeDAO.findByNetwork(network.getUuid());
        if (!nodes.isEmpty()) {
            if (nodes.stream().anyMatch(BubbleNode::isRunning)) {
                return invalid("err.network.alreadyStarted");
            }
        }

        if (!network.getState().canStartNetwork()) return invalid("err.network.cannotStartInCurrentState");

        return _startNetwork(network, cloud, region, req);
    }

    @GET @Path(EP_STATUS)
    public Response listLaunchStatuses(@Context Request req,
                                       @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        final String account = caller.admin() ? network.getAccount() : caller.getUuid();
        return ok(networkService.listLaunchStatuses(account));
    }

    @GET @Path(EP_STATUS+"/{uuid}")
    public Response requestLaunchStatus(@Context Request req,
                                        @Context ContainerRequest ctx,
                                        @PathParam("uuid") String uuid) {
        final Account caller = userPrincipal(ctx);
        final String account = caller.admin() ? network.getAccount() : caller.getUuid();
        final NodeProgressMeterTick tick = networkService.getLaunchStatus(account, uuid);
        return tick == null ? notFound(uuid) : ok(tick);
    }

    @GET @Path(EP_KEYS)
    public Response requestNetworkKeys(@Context Request req,
                                       @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        final AccountPolicy policy = policyDAO.findSingleByAccount(caller.getUuid());
        if (policy == null || !policy.hasVerifiedAccountContacts()) {
            return invalid("err.networkKeys.noVerifiedContacts");
        }
        messageDAO.create(new AccountMessage()
                .setMessageType(AccountMessageType.request)
                .setAction(AccountAction.password)
                .setTarget(ActionTarget.network)
                .setAccount(caller.getUuid())
                .setName(network.getUuid())
                .setRemoteHost(getRemoteHost(req)));
        return ok();
    }

    @GET @Path(EP_KEYS+"/{uuid}")
    public Response retrieveNetworkKeys(@Context Request req,
                                        @Context ContainerRequest ctx,
                                        @PathParam("uuid") String uuid) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        final NetworkKeys keys = keysService.retrieveKeys(uuid);
        return keys == null ? notFound(uuid) : ok(keys);
    }

    @POST @Path(EP_STOP)
    public Response stopNetwork(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!authAccount(caller)) return forbidden();

        // any nodes?
        final List<BubbleNode> nodes = nodeDAO.findByNetwork(network.getUuid());
        if (nodes.isEmpty()) log.warn("stopNetwork: no nodes found for network: "+network.getUuid());

        return ok(networkService.stopNetwork(network));
    }

    @POST @Path(EP_RESTORE)
    public Response restoreNetwork(@Context Request req,
                                   @Context ContainerRequest ctx,
                                   @QueryParam("cloud") String cloud,
                                   @QueryParam("region") String region) {
        final Account caller = userPrincipal(ctx);
        if (!authAccount(caller)) return forbidden();
        return ok(networkService.restoreNetwork(network, cloud, region, req));
    }

    @PUT @Path(EP_FORK +"/{fqdn}")
    public Response fork(@Context Request req,
                         @Context ContainerRequest ctx,
                         @PathParam("fqdn") String fqdn,
                         @QueryParam("cloud") String cloud,
                         @QueryParam("region") String region) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();

        final BubbleDomain domain = domainDAO.findByFqdn(fqdn);
        if (domain == null) return invalid("err.fqdn.domain.invalid", "domain not found for "+fqdn, fqdn);

        final String netName = domain.networkFromFqdn(fqdn);
        final BubbleNetwork network = networkDAO.findByAccountAndId(caller.getUuid(), netName);
        if (network == null) return invalid("err.fqdn.network.invalid", "network not found for "+fqdn, fqdn);

        // enable registration if current network allows it
        network.setTag(TAG_ALLOW_REGISTRATION, configuration.getThisNetwork().getBooleanTag(TAG_ALLOW_REGISTRATION, false));
        networkDAO.update(network);

        if (cloud == null) {
            cloud = configuration.getThisNode().getCloud();
        }
        if (region == null) {
            // todo: choose region that is closest to the caller's IP, but is not the same region
            region = configuration.getThisNode().getRegion();
        }

        final AccountPlan accountPlan = accountPlanDAO.findByAccountAndId(caller.getUuid(), network.getName());
        if (accountPlan == null) return invalid("err.fqdn.plan.invalid", "no plan found for network "+network.getName(), network.getName());

        network.setForkHost(network.hostFromFqdn(fqdn));

        return _startNetwork(network, cloud, region, req);
    }

    public Response _startNetwork(BubbleNetwork network,
                                  String cloud,
                                  String region,
                                  Request req) {
        if ((region != null && cloud == null) || (region == null && cloud != null)) {
            throw invalidEx("err.netlocation.invalid", "must specify both cloud and region, or neither");
        }

        final NetLocation netLocation = (region != null)
                ? NetLocation.fromCloudAndRegion(cloud, region)
                : NetLocation.fromIp(getRemoteHost(req));

        return ok(networkService.startNetwork(network, netLocation));
    }

    public boolean authAccount(Account caller) {
        // caller must be admin or owner
        return caller.admin() || caller.getUuid().equals(network.getAccount());
    }

}
