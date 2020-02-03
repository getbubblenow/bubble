package bubble.resources.account;

import bubble.dao.SessionDAO;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.account.message.AccountMessageDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.AccountPolicy;
import bubble.model.account.AuthenticatorRequest;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
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
import bubble.service.account.AuthenticatorService;
import bubble.service.account.StandardAccountMessageService;
import bubble.service.account.download.AccountDownloadService;
import bubble.service.boot.BubbleModelSetupService;
import bubble.service.cloud.StandardNetworkService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.string.LocaleUtil;
import org.cobbzilla.wizard.auth.ChangePasswordRequest;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.client.script.ApiRunner;
import org.cobbzilla.wizard.client.script.ApiRunnerListener;
import org.cobbzilla.wizard.client.script.ApiRunnerListenerStreamLogger;
import org.cobbzilla.wizard.client.script.ApiScript;
import org.cobbzilla.wizard.model.HashedPassword;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;

import static bubble.ApiConstants.*;
import static bubble.model.account.Account.validatePassword;
import static bubble.resources.account.AuthResource.forgotPasswordMessage;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.errorString;
import static org.cobbzilla.util.http.HttpContentTypes.*;
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
    @Autowired private AuthenticatorService authenticatorService;
    @Autowired private AccountMessageDAO messageDAO;

    @GET
    public Response me(@Context ContainerRequest ctx) {
        try {
            final Account account = userPrincipal(ctx);
            return ok(account.setPolicy(policyDAO.findSingleByAccount(account.getUuid())));
        } catch (Exception e) {
            return notFound();
        }
    }

    @GET @Path(EP_LOCALE)
    public Response getLocale(@Context ContainerRequest ctx) {
        final Account account = userPrincipal(ctx);
        return ok(account.getLocale());
    }

    @POST @Path(EP_LOCALE+"/{locale}")
    public Response setLocale(@Context ContainerRequest ctx,
                              @PathParam("locale") String locale) {
        final Account account = userPrincipal(ctx);
        final Account me = accountDAO.findByUuid(account.getUuid());
        if (me == null) return notFound();

        final Locale loc;
        try {
            loc = LocaleUtil.fromStringOrDie(locale); // must be valid
        } catch (Exception e) {
            return invalid("err.locale.invalid", "Invalid locale: "+locale, locale);
        }
        return ok(accountDAO.update(me.setLocale(loc.toString())));
    }

    @POST @Path(EP_CHANGE_PASSWORD)
    public Response changePassword(@Context Request req,
                                   @Context ContainerRequest ctx,
                                   ChangePasswordRequest request) {
        final Account caller = userPrincipal(ctx);

        final AccountPolicy policy = policyDAO.findSingleByAccount(caller.getUuid());
        if (policy != null && request.hasTotpToken()) {
            authenticatorService.authenticate(caller, policy, new AuthenticatorRequest()
                    .setAccount(caller.getUuid())
                    .setAuthenticate(true)
                    .setToken(request.getTotpToken()));
        }
        if (policy != null) authenticatorService.ensureAuthenticated(ctx, ActionTarget.account);
        if (!caller.getHashedPassword().isCorrectPassword(request.getOldPassword())) {
            return invalid("err.currentPassword.invalid", "current password was invalid", "");
        }
        final ConstraintViolationBean passwordViolation = validatePassword(request.getNewPassword());
        if (passwordViolation != null) return invalid(passwordViolation);

        if (policy != null) {
            final AccountMessage forgotPasswordMessage = forgotPasswordMessage(req, caller, configuration);
            final List<AccountContact> requiredApprovals = policy.getRequiredExternalApprovals(forgotPasswordMessage);
            if (!requiredApprovals.isEmpty()) {
                messageDAO.create(forgotPasswordMessage);
                return ok(caller.setMultifactorAuthList(requiredApprovals));
            }
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
        authenticatorService.ensureAuthenticated(ctx, ActionTarget.account);
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
        authenticatorService.ensureAuthenticated(ctx, ActionTarget.account);
        final AccountMessage denial = messageService.deny(caller, getRemoteHost(req), token);
        return denial != null ? ok(denial) : notFound(token);
    }

    @POST @Path(EP_DOWNLOAD)
    public Response requestDownloadAccountData(@Context Request req,
                                               @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        final AccountPolicy policy = policyDAO.findSingleByAccount(caller.getUuid());
        authenticatorService.ensureAuthenticated(ctx, policy, ActionTarget.account);
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
        authenticatorService.ensureAuthenticated(ctx, ActionTarget.account);
        final JsonNode data = downloadService.retrieveAccountData(uuid);
        return data != null ? ok(data) : notFound(uuid);
    }

    @POST @Path(EP_SCRIPT) @Produces(TEXT_PLAIN)
    public Response runScript(@Context ContainerRequest ctx,
                              JsonNode script) {
        final Account caller = userPrincipal(ctx);
        authenticatorService.ensureAuthenticated(ctx);
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

    @Path(EP_REGIONS)
    public CloudServiceRegionsResource getCloudRegions(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(CloudServiceRegionsResource.class, caller);
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

    @Path(EP_KEYS)
    public AccountSshKeysResource getSshKeys(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(AccountSshKeysResource.class, caller);
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

    @Autowired private BubbleModelSetupService modelSetupService;

    @POST @Path(EP_MODEL)
    @Consumes(MULTIPART_FORM_DATA)
    public Response uploadModel(@Context Request req,
                                @Context ContainerRequest ctx,
                                @FormDataParam("file") InputStream in,
                                @FormDataParam("name") String name) throws IOException {
        final Account caller = userPrincipal(ctx);
        authenticatorService.ensureAuthenticated(ctx);

        if (empty(name)) return invalid("err.name.required");

        @Cleanup final TempDir temp = new TempDir();
        final File modelFile = new File(temp, name);
        FileUtil.toFileOrDie(modelFile, in);

        final ApiClientBase api = configuration.newApiClient().setToken(caller.getToken());
        return ok(modelSetupService.setupModel(api, caller, modelFile));
    }

}
