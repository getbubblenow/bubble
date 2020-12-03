/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.account;

import bubble.client.BubbleNodeClient;
import bubble.dao.SessionDAO;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.account.message.AccountMessageDAO;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.BubbleNodeKeyDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.CertType;
import bubble.model.account.*;
import bubble.model.account.message.*;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.BubblePlan;
import bubble.model.boot.ActivationRequest;
import bubble.model.cloud.*;
import bubble.model.cloud.notify.NotificationReceipt;
import bubble.model.device.BubbleDeviceType;
import bubble.model.device.Device;
import bubble.server.BubbleConfiguration;
import bubble.service.account.StandardAccountMessageService;
import bubble.service.account.StandardAuthenticatorService;
import bubble.service.backup.RestoreService;
import bubble.service.bill.PromotionService;
import bubble.service.boot.ActivationService;
import bubble.service.boot.NodeManagerService;
import bubble.service.boot.SageHelloService;
import bubble.service.cloud.GeoService;
import bubble.service.device.DeviceService;
import bubble.service.notify.NotificationService;
import bubble.service.upgrade.BubbleJarUpgradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.Cleanup;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.security.RsaMessage;
import org.cobbzilla.wizard.stream.FileSendableResource;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bubble.ApiConstants.*;
import static bubble.client.BubbleNodeClient.*;
import static bubble.model.account.Account.validatePassword;
import static bubble.model.cloud.BubbleNetwork.TAG_ALLOW_REGISTRATION;
import static bubble.model.cloud.BubbleNetwork.TAG_PARENT_ACCOUNT;
import static bubble.model.cloud.notify.NotificationType.hello_to_sage;
import static bubble.model.cloud.notify.NotificationType.retrieve_backup;
import static bubble.server.BubbleConfiguration.getDEFAULT_LOCALE;
import static bubble.server.BubbleServer.getRestoreKey;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpContentTypes.*;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.LocaleUtil.currencyForLocale;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.API_TAG_UTILITY;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(AUTH_ENDPOINT)
@Service @Slf4j
public class AuthResource {

    private static final long NODE_INIT_TIMEOUT = TimeUnit.MINUTES.toMillis(2);
    private static final String DATA_ACCOUNT_NAME = "account";

    @Autowired private AccountDAO accountDAO;
    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private SessionDAO sessionDAO;
    @Autowired private ActivationService activationService;
    @Autowired private AccountMessageDAO accountMessageDAO;
    @Autowired private AccountPaymentMethodDAO accountPaymentMethodDAO;
    @Autowired private StandardAccountMessageService messageService;
    @Autowired private BubblePlanDAO planDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private StandardAuthenticatorService authenticatorService;
    @Autowired private PromotionService promoService;
    @Autowired private DeviceService deviceService;
    @Autowired private DeviceDAO deviceDAO;
    @Autowired private BubbleNodeKeyDAO nodeKeyDAO;
    @Autowired private NodeManagerService nodeManagerService;

    public Account updateLastLogin(Account account) { return accountDAO.update(account.setLastLogin()); }

    public static Account updateLastLogin(Account account, AccountDAO accountDAO) {
        return accountDAO.update(account.setLastLogin());
    }

    public Account newLoginSession(Account account) { return newLoginSession(account, accountDAO, sessionDAO); }

    public static Account newLoginSession(Account account, AccountDAO accountDAO, SessionDAO sessionDAO) {
        return account
                .setToken(sessionDAO.create(updateLastLogin(account, accountDAO)))
                .setFirstLogin(account.getLastLogin() == null ? true : null)
                .setFirstAdmin(accountDAO.isFirstAdmin(account));
    }

    @GET @Path(EP_CONFIGS)
    @Operation(tags=API_TAG_UTILITY,
            summary="Read public system configuration",
            description="Read public system configuration",
            responses=@ApiResponse(responseCode=SC_OK, description="a Map<String, Object> of public system configuration settings")
    )
    public Response getPublicSystemConfigs(@Context ContainerRequest ctx) {
        return ok(configuration.getPublicSystemConfigs());
    }

    @GET @Path(EP_READY)
    @Operation(tags=API_TAG_UTILITY,
            summary="Determine if Bubble is running and ready for login",
            description="Determine if Bubble is running and ready for login",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="empty response with status 200 if API is ready"),
                    @ApiResponse(responseCode=SC_INVALID, description="error with status 422 if API is NOT ready")
            }
    )
    public Response getNodeIsReady(@Context ContainerRequest ctx) {
        try {
            final BubbleNetwork thisNetwork = configuration.getThisNetwork();
            final BubbleNetworkState state = thisNetwork.getState();
            if (state == BubbleNetworkState.restoring || thisNetwork.sage()) return ok();
            if (deviceDAO.findByAccountAndUninitialized(accountDAO.getFirstAdmin().getUuid())
                    .stream()
                    .anyMatch(Device::configsOk)) {
                return ok();
            }
        } catch (Exception e) {
            log.warn("getNodeIsReady: "+shortError(e));
        }
        return invalid("err.node.notReady");
    }

    @GET @Path(EP_ACTIVATE)
    @Operation(tags=API_TAG_ACTIVATION,
            summary="Determine if Bubble has been activated",
            description="Determine if Bubble has been activated",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="returns true if API is activated, false otherwise",
                            content=@Content(mediaType=APPLICATION_JSON, examples={
                                    @ExampleObject(name="Bubble is activated", value="true"),
                                    @ExampleObject(name="Bubble has not been activated", value="false")
                            }))
            }
    )
    public Response isActivated(@Context ContainerRequest ctx) { return ok(accountDAO.activated()); }

    @GET @Path(EP_ACTIVATE+EP_CONFIGS)
    @Operation(tags=API_TAG_ACTIVATION,
            summary="Get activation default configuration",
            description="Get activation default configuration",
            responses=@ApiResponse(responseCode=SC_OK, description="returns an array of CloudService[] representing the default CloudServices and their settings")
    )
    public Response getActivationConfigs(@Context ContainerRequest ctx) {
        final Account caller = optionalUserPrincipal(ctx);
        if (accountDAO.activated() && (caller == null || !caller.admin())) return ok();
        return ok(activationService.getCloudDefaults());
    }

    @Transactional
    @PUT @Path(EP_ACTIVATE)
    @Operation(tags=API_TAG_ACTIVATION,
            summary="Perform one-time activation",
            description="Perform one-time activation",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the Account object for the initial admin account, with a new session token"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="forbidden if caller is not admin and activation has already been completed"),
                    @ApiResponse(responseCode=SC_INVALID, description="validation errors occurred: activation has already been completed, or there were errors processing the ActivationRequest object")
            }
    )
    public Response activate(@Context Request req,
                             @Context ContainerRequest ctx,
                             @Valid ActivationRequest request) {
        if (request == null) return invalid("err.activation.request.required");
        final Account caller = optionalUserPrincipal(ctx);
        if (caller != null) {
            if (!caller.admin()) return forbidden();
            return invalid("err.activation.alreadyDone", "activation has already been done");
        }
        if (accountDAO.activated()) {
            return invalid("err.activation.alreadyDone", "activation has already been done");
        }

        if (!request.hasEmail()) return invalid("err.email.required", "email is required");
        if (request.getEmail().contains("{{") && request.getEmail().contains("}}")) {
            request.setEmail(configuration.applyHandlebars(request.getEmail()).trim());
        }
        if (!request.hasEmail()) return invalid("err.email.required", "email is required");

        if (!request.hasPassword()) return invalid("err.password.required", "password is required");
        if (request.getPassword().contains("{{") && request.getPassword().contains("}}")) {
            request.setPassword(configuration.applyHandlebars(request.getPassword()));
        }
        if (!request.hasPassword()) return invalid("err.password.required", "password is required");

        final Account account = accountDAO.create(new Account(request).setRemoteHost(getRemoteHost(req)));
        activationService.bootstrapThisNode(account, request);

        return ok(newLoginSession(account));
    }

    @Autowired private NotificationService notificationService;
    @Autowired private SageHelloService sageHelloService;
    @Autowired private RestoreService restoreService;

    @NonNull private BubbleNode checkRestoreRequest(@Nullable final String restoreKey) {
        if (restoreKey == null) throw invalidEx("err.restoreKey.required");

        // ensure we have been initialized
        long start = now();
        while (!sageHelloService.sageHelloSuccessful() && (now() - start < NODE_INIT_TIMEOUT)) {
            sleep(SECONDS.toMillis(1), "restore: waiting for node initialization");
        }
        if (!sageHelloService.sageHelloSuccessful()) throw invalidEx("err.node.notInitialized");

        if (!restoreKey.equalsIgnoreCase(getRestoreKey())) throw invalidEx("err.restoreKey.invalid");

        final BubbleNode thisNode = configuration.getThisNode();
        if (!thisNode.hasSageNode()) throw invalidEx("err.sageNode.required");

        final BubbleNode sageNode = nodeDAO.findByUuid(thisNode.getSageNode());
        if (sageNode == null) throw invalidEx("err.sageNode.notFound");

        return sageNode;
    }

    @POST @Path(EP_RESTORE+"/{restoreKey}")
    @Operation(tags=API_TAG_BACKUP_RESTORE,
            summary="Restore a Bubble from a backup stored by sage.",
            description="Restore a Bubble from a backup stored by sage.",
            parameters=@Parameter(name="restoreKey", description="the restore key", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the NotificationReceipt from a successful request to retrieve the backup"),
                    @ApiResponse(responseCode=SC_INVALID, description="validation errors occurred")
            }
    )
    public Response restore(@NonNull @Context final Request req,
                            @NonNull @Context final ContainerRequest ctx,
                            @Nullable @PathParam("restoreKey") final String restoreKey,
                            @NonNull @Valid final NetworkKeys.EncryptedNetworkKeys encryptedKeys) {

        final var sageNode = checkRestoreRequest(restoreKey);

        final NetworkKeys keys;
        try {
            keys = encryptedKeys.decrypt();
        } catch (Exception e) {
            log.warn("restore: error decrypting keys: "+shortError(e));
            return invalid("err.networkKeys.invalid");
        }

        restoreService.registerRestore(restoreKey, keys);
        final var receipt = notificationService.notify(sageNode, retrieve_backup,
                                                       configuration.getThisNode().setRestoreKey(getRestoreKey()));

        return ok(receipt);
    }

    @POST @Path(EP_RESTORE + EP_APPLY + "/{restoreKey}")
    @Consumes(MULTIPART_FORM_DATA)
    @Operation(tags=API_TAG_BACKUP_RESTORE,
            summary="Restore a Bubble from a backup uploaded by user.",
            description="Restore a Bubble from a backup uploaded by user.",
            parameters=@Parameter(name="restoreKey", description="the restore key", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="upon success a 200 HTTP status with an empty response is returned"),
                    @ApiResponse(responseCode=SC_INVALID, description="validation errors occurred")
            }
    )
    @NonNull public Response restoreFromPackage(@NonNull @Context final Request req,
                                                @NonNull @Context final ContainerRequest ctx,
                                                @NonNull @PathParam("restoreKey") final String restoreKey,
                                                @NonNull @FormDataParam("file") final InputStream in,
                                                @NonNull @FormDataParam("password") final String password) {
        if (empty(password)) return invalid("err.password.required");

        checkRestoreRequest(restoreKey);

        restoreService.registerRestore(restoreKey, new NetworkKeys());

        try {
            if (restoreService.restoreFromPackage(restoreKey, in, password)) return ok();
        } catch (IOException e) {
            log.error("Exception while restoring from package", e);
        }
        return invalid("err.restore.failed", "Restore failed");
    }

    @POST @Path(EP_REGISTER)
    @Operation(tags=API_TAG_AUTH,
            summary="Register a new Account, starts a new API session.",
            description="Register a new Account, starts a new API session.",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the Account object that was registered, `token` property holds session token"),
                    @ApiResponse(responseCode=SC_INVALID, description="validation errors occurred")
            }
    )
    public Response register(@Context Request req,
                             @Context ContainerRequest ctx,
                             AccountRegistration request) {

        final BubbleNode thisNode = configuration.getThisNode();
        if (thisNode == null) return invalid("err.activation.required", "Node has not yet been activated");

        if (!accountDAO.activated()) return invalid("err.activation.required", "Node has not yet been activated");
        if (configuration.getUnlockKey() != null) return invalid("err.unlock.required", "Network has not yet been unlocked");

        final BubbleNetwork thisNetwork = configuration.getThisNetwork();
        if (thisNetwork == null) return invalid("err.activation.required", "Node has not yet been activated");

        if (!thisNetwork.getBooleanTag(TAG_ALLOW_REGISTRATION, false)) {
            return invalid("err.registration.disabled", "Account registration is not enabled");
        }

        final Account found = optionalUserPrincipal(ctx);
        if (found != null) return invalid("err.register.alreadyLoggedIn", "Cannot register a new account when logged in");

        request.setAdmin(false); // cannot register admins, they must be created

        final ValidationResult errors = request.validateEmail();
        if (errors.isValid()) {
            final Account existing = accountDAO.findByEmail(request.getEmail());
            if (existing != null) errors.addViolation("err.email.registered", "Email is already registered: ", request.getEmail());
        }

        final ConstraintViolationBean passwordViolation = validatePassword(request.getPassword());
        if (passwordViolation != null) errors.addViolation(passwordViolation);

        if (!request.agreeToTerms()) {
            errors.addViolation("err.terms.required", "You must agree to the legal terms to use this service");
        } else {
            request.setTermsAgreed();
        }

        String currency = null;
        if (configuration.paymentsEnabled()) {
            currency = currencyForLocale(request.getLocale(), getDEFAULT_LOCALE());
            // do we have any plans with this currency?
            if (!planDAO.getSupportedCurrencies().contains(currency)) {
                currency = currencyForLocale(getDEFAULT_LOCALE());
            }
            if (configuration.promoCodeRequired() && !request.hasPromoCode()) {
                errors.addViolation("err.promoCode.required");
            } else {
                errors.addAll(promoService.validatePromotions(request.getPromoCode(), currency));
            }
        }

        if (request.hasPreferredPlan()) {
            final BubblePlan plan = planDAO.findById(request.getPreferredPlan());
            if (plan == null) errors.addViolation("err.plan.notFound");
        }

        if (errors.isInvalid()) return invalid(errors);

        final String parentUuid = thisNetwork.getTag(TAG_PARENT_ACCOUNT, thisNetwork.getAccount());
        final Account parent = accountDAO.findByUuid(parentUuid);
        if (parent == null) return invalid("err.parent.notFound", "Parent account does not exist: "+parentUuid);

        final Account account = accountDAO.newAccount(req, null, request, parent);
        SimpleViolationException promoEx = null;
        if (configuration.paymentsEnabled()) {
            if (request.hasPaymentMethod()) {
                final AccountPaymentMethod paymentMethodObject = request.getPaymentMethodObject();
                log.info("register: found AccountPaymentMethod at registration-time: " + json(paymentMethodObject, COMPACT_MAPPER));
                paymentMethodObject.setUuid(null);
                paymentMethodObject.setAccount(account.getUuid());
                paymentMethodObject.setRequireValidatedEmail(false);
                account.waitForAccountInit(); // payment clouds for user must exist before we can create the APM
                final ValidationResult result = new ValidationResult();
                log.info("register: starting validation of payment method with requireValidatedEmail="+paymentMethodObject.requireValidatedEmail());
                paymentMethodObject.validate(result, configuration);
                if (result.isInvalid()) {
                    account.getAccountInitializer().setAbort();
                    final AccountPolicy policy = policyDAO.findSingleByAccount(account.getUuid());
                    policyDAO.update(policy.setDeletionPolicy(AccountDeletionPolicy.full_delete));
                    while (!account.getAccountInitializer().completed()) {
                        sleep(SECONDS.toMillis(1), "waiting for account initialization to complete before deleting");
                    }
                    accountDAO.delete(account.getUuid());
                    throw invalidEx(result);
                }
                log.info("register: creating AccountPaymentMethod upon registration: " + json(paymentMethodObject, COMPACT_MAPPER));
                final AccountPaymentMethod apm = accountPaymentMethodDAO.create(paymentMethodObject);
                log.info("register: created AccountPaymentMethod upon registration: " + apm.getUuid());
            }
            try {
                promoService.applyPromotions(account, request.getPromoCode(), currency);
            } catch (SimpleViolationException e) {
                promoEx = e;
            }
        }
        // When running locally and not in test mode, mark sole contact as verified
        // It will be automatically verified in AccountInitializer.
        if (configuration.getThisNetwork().local() && !configuration.testMode()) {
            final AccountContact firstContact = account.getPolicy().getAccountContacts()[0];
            account.getPolicy().verifyContact(firstContact.getUuid());
        }
        account.getAccountInitializer().setCanSendAccountMessages();
        return ok(newLoginSession(account
                .waitForAccountInit()
                .setPromoError(promoEx == null ? null : promoEx.getMessageTemplate())));
    }

    @POST @Path(EP_LOGIN)
    @Operation(tags=API_TAG_AUTH,
            summary="Login an Account, starts a new API session.",
            description="Login an Account, starts a new API session.",
            parameters=@Parameter(name="k", description="for a new Bubble that was launched with the lock enabled, the unlock key is required for the first login"),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the Account object that was logged in"),
                    @ApiResponse(responseCode=SC_INVALID, description="validation errors occurred")
            }
    )
    public Response login(@Context Request req,
                          @Context ContainerRequest ctx,
                          AccountLoginRequest request,
                          @QueryParam("k") String unlockKey) {
        if (!request.hasEmail()) return invalid("err.email.required", "email is required");
        if (!request.hasPassword()) return invalid("err.password.required", "password is required");
        final Account account = accountDAO.findByEmail(request.getEmail());
        if (account == null || account.deleted()) return notFound(request.getEmail());
        if (!account.getHashedPassword().isCorrectPassword(request.getPassword())) {
            return notFound(request.getEmail());
        }
        if (account.suspended()) return invalid("err.account.suspended");

        boolean isUnlock = false;
        if (account.locked()) {
            if (!accountDAO.locked()) {
                log.info("login: account "+account.getEmail()+" was locked, but system is unlocked, unlocking again");
                accountDAO.unlock();

            } else {
                if (empty(unlockKey)) return invalid("err.account.locked");
                if (!unlockKey.equals(configuration.getUnlockKey())) return invalid("err.unlockKey.invalid");
                // unlock all accounts
                isUnlock = true;
                log.info("login: Unlock key was valid, unlocking accounts");
                accountDAO.unlock();
            }
            accountDAO.update(account.setLocked(false));
        }

        if (!isUnlock) {
            final AccountPolicy policy = policyDAO.findSingleByAccount(account.getUuid());
            if (policy != null) {
                List<AccountContact> authFactors = policy.getAuthFactors();
                final AccountContact authenticator = authFactors.stream().filter(AccountContact::isAuthenticator).findFirst().orElse(null);
                if (authenticator != null && request.hasTotpToken()) {
                    // try totp token now
                    account.setToken(authenticatorService.authenticate(account, policy, new AuthenticatorRequest()
                            .setAccount(account.getUuid())
//                            .setAuthenticate(true)
                            .setToken(request.getTotpToken())));
                    authFactors.removeIf(AccountContact::isAuthenticator);
                }
                if (!empty(authFactors)) {
                    final AccountMessage loginRequest;
                    if (authFactors.size() == 1 && authFactors.get(0) == authenticator) {
                        // we have already authenticated, unless we didn't have a token
                        if (!request.hasTotpToken()) return invalid("err.totpToken.required");
                        loginRequest = null; // should never happen
                    } else {
                        loginRequest = accountMessageDAO.create(new AccountMessage()
                                .setAccount(account.getUuid())
                                .setNetwork(configuration.getThisNetwork().getUuid())
                                .setName(account.getUuid())
                                .setMessageType(AccountMessageType.request)
                                .setAction(AccountAction.login)
                                .setTarget(ActionTarget.account)
                                .setRemoteHost(getRemoteHost(req))
                        );
                    }
                    return ok(new Account()
                            .setEmail(account.getEmail())
                            .setLoginRequest(loginRequest != null ? loginRequest.getUuid() : null)
                            .setMultifactorAuth(AccountContact.mask(authFactors)));
                }
            }
        }

        return ok(account.hasToken() ? account : newLoginSession(account));
    }

    @POST @Path(EP_APP_LOGIN+"/{session}")
    @Operation(tags=API_TAG_AUTH,
            summary="Login an Account using an existing session token, starts a new API session. If an existing session exists, it is invalidated",
            description="Login an Account using an existing session token, starts a new API session. If an existing session exists, it is invalidated",
            parameters=@Parameter(name="session", description="the session token to use for logging in", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the Account object that was logged in"),
                    @ApiResponse(responseCode=SC_INVALID, description="validation errors occurred")
            }
    )
    public Response appLogin(@Context Request req,
                             @Context ContainerRequest ctx,
                             @PathParam("session") String sessionId) {
        Account sessionAccount = sessionDAO.find(sessionId);
        if (sessionAccount == null) {
            final BubbleNetwork thisNetwork = configuration.getThisNetwork();
            if (thisNetwork != null
                    && thisNetwork.syncAccount()
                    && thisNetwork.node()
                    && configuration.hasSageNode()
                    && !configuration.isSelfSage()) {
                // check if session is valid on sage
                @Cleanup final BubbleNodeClient sageClient = configuration.getSageNode().getApiQuickClient(configuration);
                try {
                    final Account sageAccount = sageClient.post(AUTH_ENDPOINT+EP_APP_LOGIN+"/"+sessionId, null, Account.class);
                    if (sageAccount == null || empty(sageAccount.getApiToken())) {
                        // should never happen
                        log.warn("appLogin: sageLogin succeeded, but returned null account or account without api token");
                        return notFound(sessionId);

                    } else {
                        sessionAccount = accountDAO.findByUuid(sageAccount.getUuid());
                        if (sessionAccount == null) {
                            log.warn("appLogin: sageLogin succeeded, but account does not exist locally: "+sageAccount.getUuid());
                            return notFound(sessionId);
                        }
                    }
                } catch (Exception e) {
                    log.warn("appLogin: error checking session with sage ("+configuration.getSageNode().id()+"): "+shortError(e));
                    return notFound(sessionId);
                }
            } else {
                return notFound(sessionId);
            }
        }

        final Account existing = optionalUserPrincipal(ctx);
        if (existing != null) {
            if (!existing.getUuid().equals(sessionAccount.getUuid())) {
                sessionDAO.invalidate(existing.getApiToken());
            } else {
                markAuthenticated(existing);
                return ok(existing);
            }
        }
        markAuthenticated(sessionAccount);
        return ok(sessionAccount.setApiToken(sessionDAO.create(sessionAccount)));
    }

    private void markAuthenticated(Account sessionAccount) {
        final AccountPolicy policy = policyDAO.findSingleByAccount(sessionAccount.getUuid());
        if (policy.hasVerifiedAuthenticator()) {
            authenticatorService.markAsAuthenticated(sessionAccount.getToken(), policy);
        }
    }

    @Path(EP_TRUST)
    public TrustedAuthResource getTrustedAuthResource() { return configuration.subResource(TrustedAuthResource.class); }

    @POST @Path(EP_VERIFY_KEY)
    @Operation(tags=API_TAG_NODE,
            summary="Called between Bubbles to verify RSA keys",
            description="Called between Bubbles to verify RSA keys",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="an RsaMessage object representing the encrypted challenge response"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="some error occurred, check response")
            }
    )
    public Response verifyNodeKey(@Context Request req,
                                  @Context ContainerRequest ctx,
                                  RsaMessage message) {
        final BubbleNode thisNode = configuration.getThisNode();
        if (thisNode == null) {
            log.info("verifyNodeKey: thisNode was null");
            return notFound();
        }

        final String fromNodeUuid = req.getHeader(H_BUBBLE_FROM_NODE_UUID);
        if (empty(fromNodeUuid)) {
            log.info("verifyNodeKey: header "+H_BUBBLE_FROM_NODE_UUID+" not found");
            return notFound(H_BUBBLE_FROM_NODE_UUID);
        }

        final String fromKeyUuid = req.getHeader(H_BUBBLE_FROM_NODE_KEY);
        if (empty(fromKeyUuid)) {
            log.info("verifyNodeKey: header "+H_BUBBLE_FROM_NODE_KEY+" not found");
            return notFound(H_BUBBLE_FROM_NODE_KEY);
        }

        final String toKeyUuid = req.getHeader(H_BUBBLE_TO_NODE_KEY);
        if (empty(toKeyUuid)) {
            log.info("verifyNodeKey: header "+H_BUBBLE_TO_NODE_KEY+" not found");
            return notFound(H_BUBBLE_TO_NODE_KEY);
        }

        final BubbleNodeKey fromKey = nodeKeyDAO.findByNodeAndUuid(fromNodeUuid, fromKeyUuid);
        if (fromKey == null) {
            log.info("verifyNodeKey: fromKey not found: "+fromKeyUuid);
            return notFound(fromKeyUuid);
        }

        final BubbleNodeKey verifyKey = nodeKeyDAO.findByNodeAndUuid(thisNode.getUuid(), toKeyUuid);
        if (verifyKey == null) {
            log.info("verifyNodeKey: verifyKey not found: "+toKeyUuid);
            return notFound(toKeyUuid);
        }

        try {
            final NodeKeyVerification verification = json(verifyKey.decrypt(message, fromKey.getRsaKey()), NodeKeyVerification.class);
            final String challenge = verification.getChallenge();
            final RsaMessage response = verifyKey.encrypt(challenge, fromKey.getRsaKey());
            log.info("verifyNodeKey: returning encrypted challenge: "+challenge+" to node "+fromNodeUuid);
            return ok(response);

        } catch (Exception e) {
            log.error("verifyNodeKey: "+shortError(e));
            return notFound();
        }
    }

    @POST @Path(EP_REKEY)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_UTILITY,
            summary="Re-key a node",
            description="Re-key a node. Must be admin. Creates a new NodeKey that, being newest, will be the one the node starts using",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the NodeKey that was created"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="forbidden if caller is not admin and is accessing any account Account other than themselves"),
                    @ApiResponse(responseCode=SC_INVALID, description="validation errors occurred")
            }
    )
    public Response rekeyNode(@Context Request req,
                              @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();

        final BubbleNode thisNode = configuration.getThisNode();
        if (thisNode == null) return notFound();
        return ok(nodeKeyDAO.create(new BubbleNodeKey(thisNode)));
    }

    @POST @Path("/sage_hello")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_NODE},
            summary="Send a 'hello_to_sage' message to our sage node",
            description="Send a 'hello_to_sage' message to our sage node. Must be admin. Normally a hello message is sent upon startup an every few hours thereafter. Use this endpoint to force a hello to be sent immediately.",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the NotificationReceipt for the message sent to the sage"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="forbidden if caller is not admin"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="is this node does not have a sage")
            }
    )
    public Response sageHello (@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();

        final BubbleNode selfNode = configuration.getThisNode();
        if (selfNode != null && selfNode.hasSageNode() && !selfNode.getUuid().equals(selfNode.getSageNode())) {
            final BubbleNode sageNode = nodeDAO.findByUuid(selfNode.getSageNode());
            if (sageNode != null) {
                final NotificationReceipt receipt = notificationService.notify(sageNode, hello_to_sage, selfNode);
                return ok(receipt);
            }
        }
        return notFound();
    }

    @POST @Path(EP_FORGOT_PASSWORD)
    @Operation(tags=API_TAG_AUTH,
            summary="Send a reset password message",
            description="Send a reset password message",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="an empty response with HTTP status 200 indicates success"),
                    @ApiResponse(responseCode=SC_INVALID, description="if no email address was supplied in the request body")
            }
    )
    public Response forgotPassword(@Context Request req,
                                   @Context ContainerRequest ctx,
                                   AccountLoginRequest request) {
        if (!request.hasEmail()) return invalid("err.email.required");
        final Account account = accountDAO.findById(request.getEmail());
        if (account == null) return ok();

        accountMessageDAO.create(forgotPasswordMessage(req, account, configuration));
        return ok();
    }

    public static AccountMessage forgotPasswordMessage(Request req, Account account, BubbleConfiguration configuration) {
        return new AccountMessage()
                .setAccount(account.getUuid())
                .setNetwork(configuration.getThisNetwork().getUuid())
                .setName(account.getUuid())
                .setMessageType(AccountMessageType.request)
                .setAction(AccountAction.password)
                .setTarget(ActionTarget.account)
                .setRemoteHost(getRemoteHost(req));
    }

    @POST @Path(EP_APPROVE+"/{token}")
    @Operation(tags=API_TAG_AUTH,
            summary="Approve a request",
            description="Approve a request. The token comes from an email or SMS message sent to the user.",
            parameters=@Parameter(name="token", description="the confirmation token", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="HTTP status 200 indicates success",
                            content=@Content(mediaType=APPLICATION_JSON, examples={
                                    @ExampleObject(name="if no login requested, returns an empty response", value=""),
                                    @ExampleObject(name="if login requested, returns an Account object with either a valid session token ('token' property) or additional auth factors required (check 'multifactorAuth' property)")
                            })),
                    @ApiResponse(responseCode=SC_INVALID, description="a validation error occurred, for example the token might be invalid")
            }
    )
    public Response approve(@Context Request req,
                            @Context ContainerRequest ctx,
                            @PathParam("token") String token,
                            NameAndValue[] data) {
        Account caller = optionalUserPrincipal(ctx);
        if (!empty(data)) {
            final String accountName = NameAndValue.find(data, DATA_ACCOUNT_NAME);
            final Account account = accountDAO.findById(accountName);
            if (caller != null && account != null && !caller.getUuid().equals(account.getUuid())) {
                return invalid("err.approvalToken.invalid");
            }
            if (caller == null && account == null) {
                return invalid("err.approvalToken.invalid");
            }
            caller = account;
        }
        final AccountMessage approval = messageService.approve(caller, getRemoteHost(req), token, data);
        if (approval == null) return invalid("err.approvalToken.invalid");
        final Account account = validateCallerForApproveOrDeny(caller, approval, token);

        if (approval.getMessageType() == AccountMessageType.confirmation) {
            if (account == null) return invalid("err.approvalToken.invalid");
            if (approval.getAction() == AccountAction.login || approval.getAction() == AccountAction.password) {
                return ok(newLoginSession(account));
            } else {
                return ok_empty();
            }
        }
        if (approval.getRequest() == null) {
            approval.setRequest(accountMessageDAO.findOperationRequest(approval));
            log.info("approve: set approval.request="+approval.getRequest());
        }

        return ok(messageService.determineRemainingApprovals(approval));
    }

    @POST @Path(EP_AUTHENTICATOR)
    @Operation(tags=API_TAG_AUTH,
            summary="Approve a TOTP request",
            description="Approve a TOTP request. The token comes the end user's authenticator app.",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="HTTP status 200 indicates success",
                            content=@Content(mediaType=APPLICATION_JSON, examples={
                                    @ExampleObject(name="if no login requested, returns an empty response", value=""),
                                    @ExampleObject(name="if login requested, returns an Account object with either a valid session token ('token' property) or additional auth factors required (check 'multifactorAuth' property)")
                            })),
                    @ApiResponse(responseCode=SC_INVALID, description="a validation error occurred, for example the token might be invalid")
            }
    )
    public Response authenticator(@Context Request req,
                                  @Context ContainerRequest ctx,
                                  AuthenticatorRequest request) {

        final Account caller = optionalUserPrincipal(ctx);
        final Account account = accountDAO.findById(request.getAccount());
        if (account == null) return notFound(request.getAccount());
        if (caller != null) {
            if (!caller.getUuid().equals(account.getUuid())) return invalid("err.totpToken.invalid");

            // authenticatorService requires the Account to have a token, or it will generate one
            account.setToken(caller.getToken());
        }

        final AccountPolicy policy = policyDAO.findSingleByAccount(account.getUuid());
        final AccountContact authenticator = policy.getAuthenticator();
        final String sessionToken = authenticatorService.authenticate(account, policy, request);
        if (request.authenticate()) {
            return ok_empty();

        } else if (request.verify()) {
            policyDAO.update(policy.verifyContact(policy.getAuthenticator().getUuid()));
            return ok_empty();
        }

        final AccountMessage loginRequest = accountMessageDAO.findMostRecentLoginRequest(account.getUuid());
        if (loginRequest == null) {
            log.warn("authenticator: AccountMessage (loginRequest) was null, returning OK without doing anything further");
            return ok_empty();
        }

        final AccountMessageContact amc = messageService.accountMessageContact(loginRequest, authenticator);
        if (amc == null || !amc.valid()) {
            log.warn("authenticator: AccountMessageContact was null or invalid, returning OK without doing anything further");
            return ok_empty();
        }

        final AccountMessage approval = messageService.approve(account, getRemoteHost(req), amc.key());
        if (approval == null) {
            log.warn("authenticator: AccountMessage (approval) was null, returning OK without doing anything further");
            return ok_empty();
        }

        if (approval.getMessageType() == AccountMessageType.confirmation) {
            // OK we can log in!
            return ok(updateLastLogin(account).setToken(sessionToken));
        } else {
            return ok(messageService.determineRemainingApprovals(approval));
        }
    }

    @DELETE @Path(EP_AUTHENTICATOR)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_AUTH,
            summary="Flush authenticator tokens",
            description="Flush authenticator tokens. The next operation that requires TOTP auth will require the user to re-authenticate.",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="HTTP status 200 indicates success",
                            content=@Content(mediaType=APPLICATION_JSON, examples=@ExampleObject(name="returns an empty JSON object", value="{}"))),
                    @ApiResponse(responseCode=SC_INVALID, description="a validation error occurred, for example the token might be invalid")
            }
    )
    public Response flushAuthenticatorTokens(@Context Request req,
                                             @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        authenticatorService.flush(caller.getToken());
        return ok_empty();
    }

    @POST @Path(EP_DENY+"/{token}")
    @Operation(tags=API_TAG_AUTH,
            summary="Deny a request",
            description="Deny a request. The token comes from an email or SMS message sent to the user.",
            parameters=@Parameter(name="token", description="the confirmation token", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="HTTP status 200 indicates success",
                            content=@Content(mediaType=APPLICATION_JSON, examples={
                                    @ExampleObject(name="returns the denial AccountMessage")
                            })),
                    @ApiResponse(responseCode=SC_INVALID, description="a validation error occurred, for example the token might be invalid")
            }
    )
    public Response deny(@Context Request req,
                         @Context ContainerRequest ctx,
                         @PathParam("token") String token) {

        final Account caller = optionalUserPrincipal(ctx);
        final AccountMessage denial = messageService.deny(caller, getRemoteHost(req), token);
        validateCallerForApproveOrDeny(caller, denial, token);

        return ok(denial);
    }

    @GET @Path(EP_CA_CERT)
    @Produces(CONTENT_TYPE_ANY)
    @Operation(tags=API_TAG_UTILITY,
            summary="Get the CA Certificate for this Bubble",
            description="Get the CA Certificate for this Bubble. Response body is the certificate itself, in a format determined by deviceType or type. Either deviceType or type can be supplied but not both. Device types are: `ios`, `android`, `windows`, `macos` and `linux`. Cert types are: `pem`, `p12`, `cer`, `crt`",
            parameters={
                    @Parameter(name="deviceType", description="the device type. Device types are: `ios`, `android`, `windows`, `macos` and `linux`"),
                    @Parameter(name="type", description="the certificate type. Cert types are: `pem`, `p12`, `cer`, `crt`")
            },
            responses=@ApiResponse(responseCode=SC_OK, description="HTTP status 200 indicates success, response body is certificate")
    )
    public Response getCaCert(@Context Request req,
                              @Context ContainerRequest ctx,
                              @QueryParam("deviceType") BubbleDeviceType deviceType,
                              @QueryParam("type") CertType type) {
        final Account caller = optionalUserPrincipal(ctx);
        if (type == null) {
            if (deviceType != null) {
                type = deviceType.getCertType();
            } else {
                final String remoteHost = getRemoteHost(req);
                if (!empty(remoteHost)) {
                    final Device device = deviceService.findDeviceByIp(remoteHost);
                    if (device != null) {
                        type = device.getDeviceType().getCertType();
                    }
                }
            }
        } else if (deviceType != null) {
            type = deviceType.getCertType();
        }
        if (type == null) type = CertType.pem;
        final BubbleNetwork thisNet = configuration.getThisNetwork();
        if (thisNet == null) return die("getCaCert: thisNetwork was null");
        final File certFile = new File(CACERTS_DIR, "bubble-"+thisNet.getShortId()+"-ca-cert."+type.name());
        if (!certFile.exists()) return notFound(type.name());
        return send(new FileSendableResource(certFile).setForceDownload(true));
    }

    @GET @Path(EP_KEY)
    @Operation(tags=API_TAG_UTILITY,
            summary="Get the Node Key for this Bubble",
            description="Get the Node Key for this Bubble",
            responses=@ApiResponse(responseCode=SC_OK, description="HTTP status 200 indicates success")
    )
    public Response getNodeKey(@Context Request req,
                               @Context ContainerRequest ctx) {
        final BubbleNode thisNode = configuration.getThisNode();
        if (thisNode == null) return notFound();
        final BubbleNodeKey key = nodeKeyDAO.findFirstByNode(thisNode.getUuid());
        if (key == null) return notFound(thisNode.id());
        return ok(key);
    }

    private Account validateCallerForApproveOrDeny(Account caller, AccountMessage message, String token) {
        if (message == null) throw notFoundEx(token);

        final Account account = accountDAO.findByUuid(message.getAccount());
        if (caller != null && !account.getUuid().equals(caller.getUuid())) throw forbiddenEx();

        return account;
    }

    @GET @Path(EP_LOGOUT)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_AUTH,
            summary="Logout",
            description="Logout of the current session, or logout of all sessions everywhere if the `all` parameter is true.",
            parameters=@Parameter(name="all", description="logout of all sessions everywhere"),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="HTTP status 200 indicates success"),
                    @ApiResponse(responseCode=SC_INVALID, description="If there is no current session to log out of")
            }
    )
    public Response logout(@Context ContainerRequest ctx,
                           @QueryParam("all") Boolean all) {
        final Account account = optionalUserPrincipal(ctx);
        if (account == null) return invalid("err.logout.noSession");
        if (all != null && all) {
            sessionDAO.invalidateAllSessions(account.getUuid());
        } else {
            sessionDAO.invalidate(account.getApiToken());
        }
        return ok_empty();
    }

    @POST @Path(EP_LOGOUT+"/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_AUTH,
            summary="Logout a user everywhere",
            description="Logout of the current session, or logout of all sessions everywhere if the `all` parameter is true.",
            parameters=@Parameter(name="id", description="UUID or email of user to logout", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="HTTP status 200 indicates success"),
                    @ApiResponse(responseCode=SC_INVALID, description="If there is no current session to log out of")
            }
    )
    public Response logoutUserEverywhere(@Context ContainerRequest ctx,
                                         @PathParam("id") String id) {
        final Account caller = optionalUserPrincipal(ctx);
        if (caller == null) return invalid("err.logout.noSession");
        final Account target = accountDAO.findById(id);
        if (target == null) {
            if (caller.admin()) return notFound(id);
            return forbidden();
        } else if (!target.getUuid().equals(caller.getUuid())) {
            return forbidden();
        }
        sessionDAO.invalidateAllSessions(target.getUuid());
        return ok_empty();
    }

    @GET @Path(EP_TIME)
    @Operation(tags=API_TAG_UTILITY,
            summary="Get current system time",
            description="Get current system time. Returns current time as epoch time in milliseconds",
            responses=@ApiResponse(responseCode=SC_OK, description="Returns current time as epoch time in milliseconds",
                            content=@Content(mediaType=APPLICATION_JSON, examples=@ExampleObject(name="time in milliseconds", value="1606858589683")))
    )
    public Response serverTime() { return ok(now()); }

    @Autowired private GeoService geoService;

    @GET @Path(EP_SUPPORT)
    @Operation(tags=API_TAG_UTILITY,
            summary="Get support information",
            description="Get support information for the user's current locale, if available. Use the default locale otherwise.",
            responses=@ApiResponse(responseCode=SC_OK, description="SupportInfo object")
    )
    public Response getSupportInfo (@Context Request req,
                                    @Context ContainerRequest ctx) {
        final List<String> locales = geoService.getSupportedLocales(optionalUserPrincipal(ctx), getRemoteHost(req), normalizeLangHeader(req));
        return ok(empty(locales) ? configuration.getSupport() : configuration.getSupport().forLocale(locales.get(0)));
    }

    @GET @Path(EP_SUPPORT+"/{locale}")
    @Operation(tags=API_TAG_UTILITY,
            summary="Get support information for a locale",
            description="Get support information for the given locale, if available. Use the default locale otherwise.",
            parameters=@Parameter(name="locale", description="locale to find support for", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="SupportInfo object")
    )
    public Response getSupportInfo (@Context Request req,
                                    @Context ContainerRequest ctx,
                                    @PathParam("locale") String locale) {
        return ok(configuration.getSupport().forLocale(locale));
    }

    @GET @Path(EP_APP_LINKS)
    @Operation(tags=API_TAG_UTILITY,
            summary="Get links to native applications",
            description="Get links to native applications for the current user's locale, if available. Use the default locale otherwise.",
            responses=@ApiResponse(responseCode=SC_OK, description="AppLinks object")
    )
    public Response getAppLinks (@Context Request req,
                                 @Context ContainerRequest ctx) {
        final List<String> locales = geoService.getSupportedLocales(optionalUserPrincipal(ctx), getRemoteHost(req), normalizeLangHeader(req));
        return ok(empty(locales) ? configuration.getAppLinks() : configuration.getAppLinks().forLocale(locales.get(0)));
    }

    @GET @Path(EP_APP_LINKS+"/{locale}")
    @Operation(tags=API_TAG_UTILITY,
            summary="Get links to native applications for a locale",
            description="Get links to native applications for the given locale, if available. Use the default locale otherwise.",
            parameters=@Parameter(name="locale", description="locale to find app links for", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="AppLinks object")
    )
    public Response getAppLinks (@Context Request req,
                                 @Context ContainerRequest ctx,
                                 @PathParam("locale") String locale) {
        return ok(configuration.getAppLinks().forLocale(locale));
    }

    @GET @Path(EP_PATCH+"/{token}")
    @Produces(APPLICATION_OCTET_STREAM)
    @Operation(tags=API_TAG_NODE_MANAGER,
            summary="Find a node-manager patch file",
            description="Find a node-manager patch file. The file must previously have been registered, yielding a token",
            parameters=@Parameter(name="token", description="token of the patch file to retrieve", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="raw file data", content=@Content(mediaType=APPLICATION_OCTET_STREAM)),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="token not valid")
            }
    )
    public Response getPatchFile(@Context ContainerRequest ctx,
                                 @PathParam("token") String token) {
        final File patch = nodeManagerService.findPatch(token);
        if (patch == null) return notFound(token);
        return send(new FileSendableResource(patch));
    }

    @Autowired private BubbleJarUpgradeService upgradeService;

    @GET @Path(EP_UPGRADE+"/{node}/{key}")
    @Produces(APPLICATION_OCTET_STREAM)
    @Operation(tags=API_TAG_NODE,
            summary="Return bubble jar",
            description="Return bubble jar file for upgrading other nodes to our version.",
            parameters={
                    @Parameter(name="node", description="UUID of the calling node", required=true),
                    @Parameter(name="key", description="UUID of the calling node's BubbleNodeKey", required=true)
            },
            responses={
                    @ApiResponse(responseCode=SC_OK, description="raw jar file data", content=@Content(mediaType=APPLICATION_OCTET_STREAM)),
                    @ApiResponse(responseCode=SC_UNAUTHORIZED, description="calling node is not authorized"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="token not valid")
            }
    )
    public Response getUpgrade(@Context Request req,
                               @Context ContainerRequest ctx,
                               @PathParam("node") String nodeUuid,
                               @PathParam("key") String key) {
        final String nodeForKey = upgradeService.getNodeForKey(key);
        if (nodeForKey == null) {
            log.warn("getUpgrade: key not found: "+key);
            return unauthorized();
        }
        if (!nodeForKey.equals(nodeUuid)) {
            log.warn("getUpgrade: key not for provided node");
            return unauthorized();
        }

        final BubbleNode node = nodeDAO.findByUuid(nodeForKey);
        if (node == null) {
            log.warn("getUpgrade: node not found: "+nodeForKey);
            return unauthorized();
        }

        return send(new FileSendableResource(configuration.getBubbleJar()));
    }

}
