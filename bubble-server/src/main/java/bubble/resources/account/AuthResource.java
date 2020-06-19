/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.account;

import bubble.dao.SessionDAO;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.account.message.AccountMessageDAO;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.BubbleNodeKeyDAO;
import bubble.model.CertType;
import bubble.model.account.*;
import bubble.model.account.message.*;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.BubblePlan;
import bubble.model.boot.ActivationRequest;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.BubbleNodeKey;
import bubble.model.cloud.NetworkKeys;
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
import bubble.service.cloud.DeviceIdService;
import bubble.service.notify.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.stream.FileSendableResource;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bubble.ApiConstants.*;
import static bubble.model.account.Account.validatePassword;
import static bubble.model.cloud.BubbleNetwork.TAG_ALLOW_REGISTRATION;
import static bubble.model.cloud.BubbleNetwork.TAG_PARENT_ACCOUNT;
import static bubble.model.cloud.notify.NotificationType.retrieve_backup;
import static bubble.server.BubbleConfiguration.getDEFAULT_LOCALE;
import static bubble.server.BubbleServer.getRestoreKey;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpContentTypes.*;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.LocaleUtil.currencyForLocale;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

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
    @Autowired private DeviceIdService deviceIdService;
    @Autowired private BubbleNodeKeyDAO nodeKeyDAO;
    @Autowired private NodeManagerService nodeManagerService;

    public Account updateLastLogin(Account account) { return accountDAO.update(account.setLastLogin()); }

    public String newLoginSession(Account account) {
        if (account.getLastLogin() == null) account.setFirstLogin(true);
        return sessionDAO.create(updateLastLogin(account));
    }

    @GET @Path(EP_CONFIGS)
    public Response getPublicSystemConfigs(@Context ContainerRequest ctx) {
        return ok(configuration.getPublicSystemConfigs());
    }

    @GET @Path(EP_ACTIVATE)
    public Response isActivated(@Context ContainerRequest ctx) { return ok(accountDAO.activated()); }

    @GET @Path(EP_ACTIVATE+EP_CONFIGS)
    public Response getActivationConfigs(@Context ContainerRequest ctx) {
        final Account caller = optionalUserPrincipal(ctx);
        if (accountDAO.activated() && (caller == null || !caller.admin())) return ok();
        return ok(activationService.getCloudDefaults());
    }

    @Transactional
    @PUT @Path(EP_ACTIVATE)
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

        if (!request.hasPassword()) return invalid("err.password.required", "password is required");
        if (request.getPassword().contains("{{") && request.getPassword().contains("}}")) {
            request.setPassword(configuration.applyHandlebars(request.getPassword()));
        }
        if (!request.hasPassword()) return invalid("err.password.required", "password is required");

        final Account account = accountDAO.create(new Account(request).setRemoteHost(getRemoteHost(req)));
        activationService.bootstrapThisNode(account, request);

        return ok(account.setToken(newLoginSession(account)));
    }

    @Autowired private NotificationService notificationService;
    @Autowired private SageHelloService sageHelloService;
    @Autowired private RestoreService restoreService;

    @PUT @Path(EP_RESTORE+"/{restoreKey}")
    public Response restore(@Context Request req,
                            @Context ContainerRequest ctx,
                            @PathParam("restoreKey") String restoreKey,
                            @Valid NetworkKeys.EncryptedNetworkKeys encryptedKeys) {

        // ensure we have been initialized
        long start = now();
        while (!sageHelloService.sageHelloSuccessful() && (now() - start < NODE_INIT_TIMEOUT)) {
            sleep(SECONDS.toMillis(1), "restore: waiting for node initialization");
        }
        if (!sageHelloService.sageHelloSuccessful()) {
            return invalid("err.node.notInitialized");
        }

        if (restoreKey == null) return invalid("err.restoreKey.required");
        if (!restoreKey.equalsIgnoreCase(getRestoreKey())) return invalid("err.restoreKey.invalid");

        final BubbleNode thisNode = configuration.getThisNode();
        if (!thisNode.hasSageNode()) return invalid("err.sageNode.required");

        final BubbleNode sageNode = nodeDAO.findByUuid(thisNode.getSageNode());
        if (sageNode == null) return invalid("err.sageNode.notFound");

        final NetworkKeys keys;
        try {
            keys = encryptedKeys.decrypt();
        } catch (Exception e) {
            log.warn("restore: error decrypting keys: "+shortError(e));
            return invalid("err.networkKeys.invalid");
        }

        restoreService.registerRestore(restoreKey, keys);
        final NotificationReceipt receipt = notificationService.notify(sageNode, retrieve_backup, thisNode.setRestoreKey(getRestoreKey()));

        return ok(receipt);
    }

    @POST @Path(EP_REGISTER)
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
        account.getAccountInitializer().setCanSendAccountMessages();
        return ok(account
                .waitForAccountInit()
                .setPromoError(promoEx == null ? null : promoEx.getMessageTemplate())
                .setToken(newLoginSession(account)));
    }

    @POST @Path(EP_LOGIN)
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
                            .setAuthenticate(true)
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

        return ok(account.setToken(newLoginSession(account)));
    }

    @POST @Path(EP_APP_LOGIN+"/{session}")
    public Response appLogin(@Context Request req,
                             @Context ContainerRequest ctx,
                             @PathParam("session") String sessionId) {
        final Account sessionAccount = sessionDAO.find(sessionId);
        if (sessionAccount == null) return notFound(sessionId);

        final Account existing = optionalUserPrincipal(ctx);
        if (existing != null) {
            sessionDAO.invalidate(existing.getApiToken());
        }
        return ok(sessionAccount.setApiToken(sessionDAO.create(sessionAccount)));
    }

    @POST @Path(EP_FORGOT_PASSWORD)
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
                return ok(account.setToken(newLoginSession(account)));
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
    public Response flushAuthenticatorTokens(@Context Request req,
                                  @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        authenticatorService.flush(caller.getToken());
        return ok_empty();
    }

    @POST @Path(EP_DENY+"/{token}")
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
                    final Device device = deviceIdService.findDeviceByIp(remoteHost);
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
        final File certFile = new File(CACERTS_DIR, thisNet.getNetworkDomain()+"-ca-cert."+type.name());
        if (!certFile.exists()) return notFound(type.name());
        return send(new FileSendableResource(certFile).setForceDownload(true));
    }

    @GET @Path(EP_KEY)
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

    @GET @Path(EP_PATCH+"/{token}")
    @Produces(APPLICATION_OCTET_STREAM)
    public Response getPatchFile(@Context ContainerRequest ctx,
                                 @PathParam("token") String token) {
        final File patch = nodeManagerService.findPatch(token);
        if (patch == null) return notFound(token);
        return send(new FileSendableResource(patch));
    }

}
