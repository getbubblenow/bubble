/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
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
import bubble.model.device.BubbleDeviceType;
import bubble.resources.app.AppsResource;
import bubble.resources.bill.*;
import bubble.resources.cloud.*;
import bubble.resources.driver.DriversResource;
import bubble.resources.notify.ReceivedNotificationsResource;
import bubble.resources.notify.SentNotificationsResource;
import bubble.server.BubbleConfiguration;
import bubble.service.account.StandardAccountMessageService;
import bubble.service.account.StandardAuthenticatorService;
import bubble.service.account.download.AccountDownloadService;
import bubble.service.boot.BubbleJarUpgradeService;
import bubble.service.boot.BubbleModelSetupService;
import bubble.service.boot.SageHelloService;
import bubble.service.cloud.NodeLaunchMonitor;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.string.LocaleUtil;
import org.cobbzilla.wizard.api.CrudOperation;
import org.cobbzilla.wizard.auth.ChangePasswordRequest;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.client.script.ApiRunner;
import org.cobbzilla.wizard.client.script.ApiRunnerListener;
import org.cobbzilla.wizard.client.script.ApiRunnerListenerStreamLogger;
import org.cobbzilla.wizard.client.script.ApiScript;
import org.cobbzilla.wizard.model.HashedPassword;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.server.config.ErrorApiConfiguration;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static bubble.ApiConstants.*;
import static bubble.model.account.Account.validatePassword;
import static bubble.resources.account.AuthResource.forgotPasswordMessage;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
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
    @Autowired private StandardAuthenticatorService authenticatorService;
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

    @Getter(lazy=true) private final Map<String, String> errorApi = initErrorApi();
    private Map<String, String> initErrorApi() {
        final Map<String, String> err = new HashMap<>(2, 1.0f);
        if (configuration != null) {
            final ErrorApiConfiguration errorApi = configuration.getErrorApi();
            if (errorApi != null) {
                err.put("url", errorApi.getUrl());
                err.put("key", errorApi.getKey());
            }
        }
        return err;
    }

    @GET @Path(EP_ERROR_API)
    public Response errorApi(@Context Request req) { return ok(getErrorApi()); }

    @POST @Path(EP_CHANGE_PASSWORD)
    public Response changePassword(@Context Request req,
                                   @Context ContainerRequest ctx,
                                   ChangePasswordRequest request) {
        final Account caller = userPrincipal(ctx);
        final Account callerAccount = accountDAO.findByUuid(caller.getUuid());  // refresh caller to ensure HashedPassword is populated
        if (callerAccount == null) return notFound();
        caller.setHashedPassword(callerAccount.getHashedPassword());

        final AccountPolicy policy = policyDAO.findSingleByAccount(caller.getUuid());
        if (policy != null && request.hasTotpToken()) {
            authenticatorService.authenticate(caller, policy, new AuthenticatorRequest()
                    .setAccount(caller.getUuid())
                    .setAuthenticate(true)
                    .setToken(request.getTotpToken()));
        }
        if (policy != null) authenticatorService.ensureAuthenticated(ctx, ActionTarget.account);

        if (policy != null) {
            final AccountMessage forgotPasswordMessage = forgotPasswordMessage(req, caller, configuration);
            final List<AccountContact> requiredApprovals = policy.getRequiredApprovals(forgotPasswordMessage);
            final List<AccountContact> requiredExternalApprovals = policy.getRequiredExternalApprovals(forgotPasswordMessage);
            if (!requiredApprovals.isEmpty()) {
                if (requiredApprovals.stream().anyMatch(AccountContact::isAuthenticator)) {
                    if (!request.hasTotpToken()) return invalid("err.totpToken.required");
                    authenticatorService.authenticate(caller, policy, new AuthenticatorRequest()
                            .setAccount(caller.getUuid())
                            .setAuthenticate(true)
                            .setToken(request.getTotpToken()));
                }
                if (!requiredExternalApprovals.isEmpty()) {
                    messageDAO.create(forgotPasswordMessage);
                    return ok(caller.setMultifactorAuthList(requiredApprovals));
                }
            }
        }

        if (!caller.getHashedPassword().isCorrectPassword(request.getOldPassword())) {
            return invalid("err.currentPassword.invalid", "current password was invalid", "");
        }
        final ConstraintViolationBean passwordViolation = validatePassword(request.getNewPassword());
        if (passwordViolation != null) return invalid(passwordViolation);

        // Set new password, update account, and write back to session
        final Account updated = accountDAO.update(caller.setHashedPassword(new HashedPassword(request.getNewPassword())));
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
    public AccountPlansResource getAllPlans(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(AccountPlansResource.class, caller);
    }

    @Path(EP_CURRENT_PLANS)
    public CurrentAccountPlansResource getCurrentPlans(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(CurrentAccountPlansResource.class, caller);
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

    @GET @Path(EP_DEVICE_TYPES)
    public Response getDeviceTypes(@Context ContainerRequest ctx) {
        return ok(BubbleDeviceType.getSelectableTypes());
    }

    @Path(EP_REFERRAL_CODES)
    public ReferralCodesResource getReferralCodes(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(ReferralCodesResource.class, caller);
    }

    @Autowired private NodeLaunchMonitor launchMonitor;

    @GET @Path(EP_STATUS)
    public Response listLaunchStatuses(@Context Request req,
                                       @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return ok(launchMonitor.listLaunchStatuses(caller.getUuid()));
    }

    @Path(EP_PROMOTIONS)
    public AccountPromotionsResource getPromotionsResource(@Context Request req,
                                                           @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(AccountPromotionsResource.class, caller);
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

        @Cleanup final ApiClientBase api = configuration.newApiClient().setToken(caller.getToken());
        final Map<CrudOperation, Collection<Identifiable>> model = modelSetupService.setupModel(api, caller, modelFile);
        return ok(model);
    }

    @Autowired private SageHelloService sageHelloService;
    @Autowired private BubbleJarUpgradeService jarUpgradeService;

    private final AtomicLong lastUpgradeCheck = new AtomicLong(0);
    private static final long UPGRADE_CHECK_INTERVAL = MINUTES.toMillis(5);

    @GET @Path(EP_UPGRADE)
    public Response checkForUpgrade(@Context Request req,
                                    @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        authenticatorService.ensureAuthenticated(ctx);

        synchronized (lastUpgradeCheck) {
            if (now() - lastUpgradeCheck.get() > UPGRADE_CHECK_INTERVAL) {
                lastUpgradeCheck.set(now());
                sageHelloService.interrupt();
            }
        }
        return ok_empty();
    }

    @POST @Path(EP_UPGRADE)
    public Response upgrade(@Context Request req,
                            @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        authenticatorService.ensureAuthenticated(ctx);

        background(() -> jarUpgradeService.upgrade());
        return ok(configuration.getPublicSystemConfigs());
    }

}
