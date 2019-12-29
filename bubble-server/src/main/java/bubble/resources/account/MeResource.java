package bubble.resources.account;

import bubble.dao.SessionDAO;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.resources.app.AppsResource;
import bubble.resources.bill.AccountPaymentMethodsResource;
import bubble.resources.bill.AccountPaymentsResource;
import bubble.resources.bill.AccountPlansResource;
import bubble.resources.bill.BillsResource;
import bubble.resources.cloud.*;
import bubble.resources.driver.DriversResource;
import bubble.resources.notify.ReceivedNotificationsResource;
import bubble.resources.notify.SentNotificationsResource;
import bubble.server.BubbleConfiguration;
import bubble.service.account.StandardAccountMessageService;
import bubble.service.account.download.AccountDownloadService;
import bubble.service.cloud.StandardNetworkService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.auth.ChangePasswordRequest;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.client.script.ApiRunner;
import org.cobbzilla.wizard.client.script.ApiRunnerListener;
import org.cobbzilla.wizard.client.script.ApiRunnerListenerStreamLogger;
import org.cobbzilla.wizard.client.script.ApiScript;
import org.cobbzilla.wizard.model.HashedPassword;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.StringWriter;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.errorString;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpContentTypes.TEXT_PLAIN;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(ME_ENDPOINT)
@Service @Slf4j
public class MeResource {

    @Autowired private AccountDAO accountDAO;
    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private SessionDAO sessionDAO;
    @Autowired private AccountDownloadService downloadService;
    @Autowired private BubbleConfiguration configuration;

    @GET
    public Response me(@Context ContainerRequest ctx) {
        try {
            final Account account = userPrincipal(ctx);
            return ok(account.setPolicy(policyDAO.findSingleByAccount(account.getUuid())));
        } catch (Exception e) {
            return notFound();
        }
    }

    @POST @Path(EP_CHANGE_PASSWORD)
    public Response changePassword(@Context ContainerRequest ctx,
                                   ChangePasswordRequest request) {
        final Account caller = userPrincipal(ctx);
        if (!caller.getHashedPassword().isCorrectPassword(request.getOldPassword())) {
            return invalid("err.oldPassword.invalid", "old password was invalid");
        }
        caller.setHashedPassword(new HashedPassword(request.getNewPassword()));

        // Update account, and write back to session
        final Account updated = accountDAO.update(caller);
        sessionDAO.update(caller.getApiToken(), updated);

        return ok(updated);
    }

    @Autowired private StandardAccountMessageService messageService;

    @POST @Path(EP_APPROVE+"/{token}")
    public Response approve(@Context Request req,
                            @Context ContainerRequest ctx,
                            @PathParam("token") String token) {
        final Account caller = userPrincipal(ctx);
        final AccountMessage approval = messageService.approve(caller, getRemoteHost(req), token);
        if (approval == null) return notFound(token);

        if (approval.getMessageType() == AccountMessageType.confirmation) {
            return ok(approval);
        } else {
            return ok(messageService.determineRemainingApprovals(approval));
        }
    }

    @POST @Path(EP_DENY+"/{token}")
    public Response deny(@Context Request req,
                         @Context ContainerRequest ctx,
                         @PathParam("token") String token) {
        final Account caller = userPrincipal(ctx);
        final AccountMessage denial = messageService.deny(caller, getRemoteHost(req), token);
        return denial != null ? ok(denial) : notFound(token);
    }

    @POST @Path(EP_DOWNLOAD)
    public Response requestDownloadAccountData(@Context Request req,
                                               @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        final AccountPolicy policy = policyDAO.findSingleByAccount(caller.getUuid());
        if (policy == null || !policy.hasVerifiedAccountContacts()) {
            return invalid("err.download.noVerifiedContacts");
        }
        downloadService.downloadAccountData(req, caller.getUuid());
        return ok();
    }

    @POST @Path(EP_DOWNLOAD+"/{uuid}")
    public Response downloadAccountData(@Context Request req,
                                        @Context ContainerRequest ctx,
                                        @PathParam("uuid") String uuid) {
        final Account caller = userPrincipal(ctx);
        final JsonNode data = downloadService.retrieveAccountData(uuid);
        return data != null ? ok(data) : notFound(uuid);
    }

    @POST @Path(EP_SCRIPT) @Produces(TEXT_PLAIN)
    public Response runScript(@Context ContainerRequest ctx,
                              JsonNode script) {
        final Account caller = userPrincipal(ctx);
        final StringWriter writer = new StringWriter();
        final ApiRunnerListener listener = new ApiRunnerListenerStreamLogger("runScript", writer);
        @Cleanup final ApiClientBase api = configuration.newApiClient();
        final ApiRunner runner = new ApiRunner(api, listener);
        try {
            if (script.isArray()) {
                runner.run(json(json(script), ApiScript[].class));
            } else {
                runner.run(json(json(script), ApiScript.class));
            }
        } catch (Exception e) {
            writer.write("error processing script: "+errorString(e));
        }
        writer.flush();
        return ok(writer.getBuffer().toString());
    }

    @Path(EP_APPS)
    public AppsResource getApps(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(AppsResource.class, caller);
    }

    @Path(EP_DRIVERS)
    public DriversResource getDrivers(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(DriversResource.class, caller);
    }

    @Path(EP_NODES)
    public NodesResource getNodes(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(NodesResource.class, caller);
    }

    @Path(EP_CLOUDS)
    public CloudServicesResource getClouds(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(CloudServicesResource.class, caller);
    }

    @Path(EP_DOMAINS)
    public DomainsResource getDomains(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(DomainsResource.class, caller);
    }

    @Path(EP_NETWORKS)
    public NetworksResource getNetworks(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(NetworksResource.class, caller);
    }

    @Path(EP_ROLES)
    public AnsibleRolesResource getAnsibleRoles(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(AnsibleRolesResource.class, caller);
    }

    @Path(EP_SENT_NOTIFICATIONS)
    public SentNotificationsResource getSentNotificationsResource(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(SentNotificationsResource.class, caller);
    }

    @Path(EP_RECEIVED_NOTIFICATIONS)
    public ReceivedNotificationsResource getReceivedNotificationsResource(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(ReceivedNotificationsResource.class, caller);
    }

    @Path(EP_PLANS)
    public AccountPlansResource getPlans(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(AccountPlansResource.class, caller);
    }

    @Path(EP_PAYMENT_METHODS)
    public AccountPaymentMethodsResource getAccountPaymentMethods(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(AccountPaymentMethodsResource.class, caller);
    }

    @Path(EP_BILLS)
    public BillsResource getBills(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(BillsResource.class, caller);
    }

    @Path(EP_PAYMENTS)
    public AccountPaymentsResource getPayments(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(AccountPaymentsResource.class, caller);
    }

    @Path(EP_FOOTPRINTS)
    public FootprintsResource getFootprints(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(FootprintsResource.class, caller);
    }

    @Path(EP_DEVICES)
    public DevicesResource getDevices(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(DevicesResource.class, caller);
    }

    @Autowired private StandardNetworkService networkService;

    @GET @Path(EP_STATUS)
    public Response listLaunchStatuses(@Context Request req,
                                       @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return ok(networkService.listLaunchStatuses(caller.getUuid()));
    }

}
