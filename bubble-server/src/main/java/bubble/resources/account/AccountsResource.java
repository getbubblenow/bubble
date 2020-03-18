/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.resources.account;

import bubble.cloud.CloudServiceType;
import bubble.dao.SessionDAO;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.account.message.AccountMessageDAO;
import bubble.model.account.*;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.model.cloud.BubbleNetwork;
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
import bubble.service.account.StandardAuthenticatorService;
import bubble.service.account.MitmControlService;
import bubble.service.account.download.AccountDownloadService;
import bubble.service.boot.SelfNodeService;
import bubble.service.cloud.StandardNetworkService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.auth.ChangePasswordRequest;
import org.cobbzilla.wizard.model.HashedPassword;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

import static bubble.ApiConstants.*;
import static bubble.model.account.Account.ADMIN_UPDATE_FIELDS;
import static bubble.model.account.Account.validatePassword;
import static bubble.resources.account.AuthResource.forgotPasswordMessage;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@SuppressWarnings("RSReferenceInspection")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(ACCOUNTS_ENDPOINT)
@Service @Slf4j
public class AccountsResource {

    @Autowired private BubbleConfiguration configuration;
    @Autowired private AccountDAO accountDAO;
    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private AccountMessageDAO messageDAO;
    @Autowired private AccountDownloadService downloadService;
    @Autowired private StandardAuthenticatorService authenticatorService;
    @Autowired private SelfNodeService selfNodeService;
    @Autowired private SessionDAO sessionDAO;

    @GET
    public Response list(@Context ContainerRequest ctx) {
        final AccountContext c = new AccountContext(ctx);
        return ok(accountDAO.findAll());
    }

    @GET @Path("/{id}")
    public Response findUser(@Context ContainerRequest ctx,
                             @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return ok(c.account);
    }

    @PUT
    public Response createUser(@Context Request req,
                               @Context ContainerRequest ctx,
                               AccountRegistration request) {

        final AccountContext c = new AccountContext(ctx, request.getName(), true);

        // only admins can use this endpoint
        // regular users must use AuthResource.register
        if (!c.caller.admin()) return forbidden();

        if (c.account != null) return invalid("err.user.exists", "User with name "+request.getName()+" already exists", request.getName());

        final ValidationResult errors = new ValidationResult();
        final ConstraintViolationBean passwordViolation = validatePassword(request.getPassword());
        if (passwordViolation != null) errors.addViolation(passwordViolation);
        if (!request.hasContact()) {
            errors.addViolation("err.contact.required", "No contact information provided", request.getName());
        } else {
            request.getContact().validate(errors);
        }
        if (!request.agreeToTerms()) {
            errors.addViolation("err.terms.required", "You must agree to the legal terms to use this service");
        } else {
            request.setTermsAgreed();
        }
        if (errors.isInvalid()) return invalid(errors);

        final String parentUuid;
        if (request.hasParent()) {
            parentUuid = request.getParent();
        } else {
            final BubbleNetwork thisNetwork = configuration.getThisNetwork();
            if (thisNetwork != null) {
                parentUuid = thisNetwork.getAccount();
            } else {
                final Account firstAdmin = accountDAO.getFirstAdmin();
                if (firstAdmin == null) return invalid("err.user.noAdmin");
                parentUuid = firstAdmin.getUuid();
            }
        }
        final Account parent = parentUuid.equalsIgnoreCase(c.caller.getUuid()) ? c.caller : accountDAO.findByUuid(parentUuid);
        if (parent == null) return invalid("err.parent.notFound", "Parent account does not exist: "+parentUuid);

        final AccountRegistration reg = (AccountRegistration) request
                .setRemoteHost(getRemoteHost(req))
                .setVerifyContact(true);
        final Account created = accountDAO.newAccount(req, c.caller, reg, parent);
        created.getAccountInitializer().setCanSendAccountMessages();
        return ok(created.waitForAccountInit());
    }

    @POST @Path("/{id}"+EP_DOWNLOAD)
    public Response downloadAllUserData(@Context Request req,
                                        @Context ContainerRequest ctx,
                                        @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        if (!c.caller.admin()) return forbidden();

        authenticatorService.ensureAuthenticated(ctx, ActionTarget.account);

        final Map<String, List<String>> data = downloadService.downloadAccountData(req, id, false);
        return data != null ? ok(data) : invalid("err.download.error");
    }

    @POST @Path("/{id}")
    public Response updateUser(@Context ContainerRequest ctx,
                               @PathParam("id") String id,
                               Account request) {
        final AccountContext c = new AccountContext(ctx, id);
        authenticatorService.ensureAuthenticated(ctx, ActionTarget.account);

        if (c.caller.admin()) {
            if (c.caller.getUuid().equals(c.account.getUuid())) {
                // admins cannot suspend themselves
                if (request.suspended()) return invalid("err.suspended.cannotSuspendSelf");
                // admins cannot un-admin themselves
                if (!request.admin()) return invalid("err.admin.cannotRemoveAdminStatusFromSelf");
            }
            c.account.update(request, ADMIN_UPDATE_FIELDS);
        } else {
            c.account.update(request);
        }
        return ok(accountDAO.update(c.account));
    }

    @Autowired private StandardNetworkService networkService;

    @GET @Path("/{id}"+EP_STATUS)
    public Response listLaunchStatuses(@Context Request req,
                                       @Context ContainerRequest ctx,
                                       @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return ok(networkService.listLaunchStatuses(c.account.getUuid()));
    }

    @Path("/{id}"+EP_PROMOTIONS)
    public AccountPromotionsResource getPromotionsResource(@Context Request req,
                                          @Context ContainerRequest ctx,
                                          @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(AccountPromotionsResource.class, c.account);
    }

    @GET @Path("/{id}"+EP_POLICY)
    public Response viewPolicy(@Context ContainerRequest ctx,
                               @PathParam("id") String id) {
        final AccountsResource.AccountContext c = new AccountsResource.AccountContext(ctx, id);
        final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());
        authenticatorService.ensureAuthenticated(ctx, policy, ActionTarget.account);
        return policy == null ? notFound(id) : ok(policy.mask());
    }

    @POST @Path("/{id}"+EP_POLICY)
    public Response updatePolicy(@Context ContainerRequest ctx,
                                 @PathParam("id") String id,
                                 AccountPolicy request) {
        final AccountContext c = new AccountContext(ctx, id);
        AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());
        if (policy == null) {
            policy = policyDAO.create(new AccountPolicy(request).setAccount(c.account.getUuid()));
        } else {
            authenticatorService.ensureAuthenticated(ctx, policy, ActionTarget.account);
            if (policy.authenticatorTimeoutChanged(request)) {
                authenticatorService.updateExpiration(ctx, policy);
            }
            policy = policyDAO.update((AccountPolicy) policy.update(request));
        }

        return ok(policy.mask());
    }

    @POST @Path("/{id}"+EP_POLICY+EP_CONTACTS)
    public Response setContact(@Context Request req,
                               @Context ContainerRequest ctx,
                               @PathParam("id") String id,
                               @Valid AccountContact contact) {
        final AccountContext c = new AccountContext(ctx, id);
        final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());
        authenticatorService.ensureAuthenticated(ctx, policy, ActionTarget.account);

        final AccountContact existing = policy.findContact(contact);
        if (existing != null) {
            // if it already exists, these fields cannot be changed
            contact.setUuid(existing.getUuid());
            contact.setType(existing.getType());
            contact.setInfo(existing.getInfo());
        }

        policyDAO.update(policy.setContact(contact, c.account, configuration));
        final AccountContact added = policy.findContact(contact);
        if (added == null) {
            log.error("setContact: contact not set: "+contact);
            return serverError();
        }
        if (!added.verified()) {
            log.info("setContact: contact is new, sending verify message");
            messageDAO.sendVerifyRequest(getRemoteHost(req), c.account, contact);
        }
        return ok(existing != null ? added.mask() : added);
    }

    @POST @Path("/{id}"+EP_POLICY+EP_CONTACTS+"/verify")
    public Response sendVerification(@Context Request req,
                                     @Context ContainerRequest ctx,
                                     @PathParam("id") String id,
                                     @Valid AccountContact contact) {
        final AccountContext c = new AccountContext(ctx, id);

        if (!contact.getType().isVerifiableAuthenticationType()) {
            return invalid("err.type.notVerifiable", "type is not verifiable: "+contact.getType());
        }
        final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());
        final AccountContact found = policy.findContact(contact);
        if (found == null) return notFound(contact.getType()+"/"+contact.getInfo());
        messageDAO.sendVerifyRequest(getRemoteHost(req), c.account, found);
        return ok(policy.mask());
    }

    @GET @Path("/{id}"+EP_POLICY+EP_CONTACTS+"/{type}/{info}")
    public Response findContact(@Context ContainerRequest ctx,
                                @PathParam("id") String id,
                                @PathParam("type") CloudServiceType type,
                                @PathParam("info") String info) {
        final AccountContext c = new AccountContext(ctx, id);
        final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());
        authenticatorService.ensureAuthenticated(ctx, policy, ActionTarget.account);
        final AccountContact contact = policy.findContact(new AccountContact().setType(type).setInfo(info));
        return contact != null ? ok(contact.mask()) : notFound(type+"/"+info);
    }

    @DELETE @Path("/{id}"+EP_POLICY+EP_CONTACTS+"/{type}/{info}")
    public Response removeContact(@Context ContainerRequest ctx,
                                  @PathParam("id") String id,
                                  @PathParam("type") CloudServiceType type,
                                  @PathParam("info") String info) {
        final AccountContext c = new AccountContext(ctx, id);
        if (type == CloudServiceType.authenticator) return invalid("err.info.invalid", "info should be empty for authenticator");
        final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());
        authenticatorService.ensureAuthenticated(ctx, policy, ActionTarget.account);
        final AccountContact contact = policy.findContact(new AccountContact().setType(type).setInfo(info));
        if (contact == null) return notFound(type.name()+"/"+info);
        return ok(policyDAO.update(policy.removeContact(contact)).mask());
    }

    @DELETE @Path("/{id}"+EP_POLICY+EP_CONTACTS+EP_AUTHENTICATOR)
    public Response removeAuthenticator(@Context ContainerRequest ctx,
                                        @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());

        authenticatorService.ensureAuthenticated(ctx, policy, ActionTarget.account);

        final AccountContact contact = policy.findContact(new AccountContact().setType(CloudServiceType.authenticator));
        if (contact == null) return notFound(CloudServiceType.authenticator.name());

        final AccountPolicy updated = policyDAO.update(policy.removeContact(contact)).mask();
        authenticatorService.flush(c.caller.getToken());

        return ok(updated);
    }

    @DELETE @Path("/{id}"+EP_POLICY+EP_CONTACTS+"/{uuid}")
    public Response removeContactByUuid(@Context ContainerRequest ctx,
                                        @PathParam("id") String id,
                                        @PathParam("uuid") String uuid) {
        final AccountContext c = new AccountContext(ctx, id);
        final AccountPolicy policy = policyDAO.findSingleByAccount(c.caller.getUuid());
        authenticatorService.ensureAuthenticated(ctx, policy, ActionTarget.account);

        final AccountContact found = policy.findContact(new AccountContact().setUuid(uuid));
        if (found == null) return notFound(uuid);
        return ok(policyDAO.update(policy.removeContact(found)).mask());
    }

    @DELETE @Path("/{id}"+EP_REQUEST)
    public Response requestDeleteUser(@Context Request req,
                                      @Context ContainerRequest ctx,
                                      @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);

        authenticatorService.ensureAuthenticated(ctx, ActionTarget.account);

        // request deletion
        return ok(messageDAO.create(new AccountMessage()
                .setMessageType(AccountMessageType.request)
                .setAction(AccountAction.delete)
                .setTarget(ActionTarget.account)
                .setAccount(c.account.getUuid())
                .setNetwork(selfNodeService.getThisNetwork().getUuid())
                .setName(c.account.getUuid())
                .setRemoteHost(getRemoteHost(req))));
    }


    @POST @Path("/{id}"+EP_CHANGE_PASSWORD)
    public Response rootChangePassword(@Context Request req,
                                       @Context ContainerRequest ctx,
                                       @PathParam("id") String id,
                                       ChangePasswordRequest request) {
        final AccountContext c = new AccountContext(ctx, id);
        if (!c.caller.admin()) return forbidden();
        final Account caller = accountDAO.findByUuid(c.caller.getUuid()).setToken(c.caller.getToken());

        final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());

        // if there was a totp token provided, try it
        final String authSession;
        if (request.hasTotpToken()) {
            c.account.setToken(authenticatorService.authenticate(caller, policy, new AuthenticatorRequest()
                    .setAccount(c.account.getUuid())
                    .setAuthenticate(true)
                    .setToken(request.getTotpToken())));
        } else {
            authSession = null;
        }

        // older admin, or self (since ctime would be equal)
        final boolean olderAdmin = c.account.admin() && caller.getCtime() >= c.account.getCtime();

        // admins created earlier can do anything to admins created later
        // if admins created later try to change password of admins created earlier, we go through approvals
        if (policy != null && olderAdmin) {
            // ensure authenticator has been provided for account
            authenticatorService.ensureAuthenticated(c.account, policy, ActionTarget.account);

            // if caller is younger admin, they must know the current password of the admin whose password they're trying to change
            if (!c.account.getHashedPassword().isCorrectPassword(request.getOldPassword())) {
                return invalid("err.currentPassword.invalid", "current password was invalid", "");
            }

            final AccountMessage forgotPasswordMessage = forgotPasswordMessage(req, c.account, configuration);
            final List<AccountContact> requiredApprovals = policy.getRequiredApprovals(forgotPasswordMessage);
            final List<AccountContact> requiredExternalApprovals = policy.getRequiredExternalApprovals(forgotPasswordMessage);
            if (!requiredApprovals.isEmpty()) {
                if (requiredApprovals.stream().anyMatch(AccountContact::isAuthenticator)) {
                    if (!request.hasTotpToken()) return invalid("err.totpToken.required");
                    authenticatorService.authenticate(caller, policy, new AuthenticatorRequest()
                            .setAccount(c.account.getUuid())
                            .setAuthenticate(true)
                            .setToken(request.getTotpToken()));
                }
                if (!requiredExternalApprovals.isEmpty()) {
                    messageDAO.create(forgotPasswordMessage);
                    return ok(c.account.setMultifactorAuthList(requiredApprovals));
                }
            }
        }

        // validate new password
        final ConstraintViolationBean passwordViolation = validatePassword(request.getNewPassword());
        if (passwordViolation != null) return invalid(passwordViolation);

        // Set password, update account. Update session if caller is targeting self, otherwise invalidate all sessions
        final Account updated = accountDAO.update(c.account.setHashedPassword(new HashedPassword(request.getNewPassword())));
        if (caller.getUuid().equals(c.account.getUuid())) {
            sessionDAO.update(caller.getApiToken(), updated);
        } else {
            sessionDAO.invalidateAllSessions(c.account.getUuid());
        }
        return ok(updated);
    }

    @DELETE @Path("/{id}")
    public Response rootDeleteUser(@Context ContainerRequest ctx,
                                   @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        if (!c.caller.admin()) return forbidden();
        if (c.caller.getUuid().equals(c.account.getUuid())) {
            return invalid("err.delete.cannotDeleteSelf");
        }

        authenticatorService.ensureAuthenticated(ctx, ActionTarget.account);

        if (c.account.deleted()) {
            // admin is deleting an account that is already flagged 'deleted'
            // force a full deletion
            final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());
            if (policy != null) {
                policyDAO.update(policy.setDeletionPolicy(AccountDeletionPolicy.full_delete));
            } else {
                policyDAO.create(new AccountPolicy()
                        .setAccount(c.account.getUuid())
                        .setDeletionPolicy(AccountDeletionPolicy.full_delete));
            }
        }
        accountDAO.delete(c.account.getUuid());
        return ok(c.account);
    }

    @Autowired private MitmControlService mitmControlService;

    @GET @Path("/{id}"+EP_MITM)
    public Response mitmStatus(@Context ContainerRequest ctx,
                               @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        if (!c.caller.admin()) return forbidden();
        return ok(mitmControlService.getEnabled());
    }

    @POST @Path("/{id}"+EP_MITM+EP_ENABLE)
    public Response mitmOn(@Context ContainerRequest ctx,
                           @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        if (!c.caller.admin()) return forbidden();
        return ok(mitmControlService.setEnabled(true));
    }

    @POST @Path("/{id}"+EP_MITM+EP_DISABLE)
    public Response mitmOff(@Context ContainerRequest ctx,
                            @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        if (!c.caller.admin()) return forbidden();
        return ok(mitmControlService.setEnabled(false));
    }

    @Path("/{id}"+EP_APPS)
    public AppsResource getSites(@Context ContainerRequest ctx,
                                 @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(AppsResource.class, c.account);
    }

    @Path("/{id}"+EP_DRIVERS)
    public DriversResource getDrivers(@Context ContainerRequest ctx,
                                      @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(DriversResource.class, c.account);
    }

    @Path("/{id}"+EP_CLOUDS)
    public CloudServicesResource getClouds(@Context ContainerRequest ctx,
                                   @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(CloudServicesResource.class, c.account);
    }

    @Path("/{id}"+EP_DOMAINS)
    public DomainsResource getDomains(@Context ContainerRequest ctx,
                                      @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(DomainsResource.class, c.account);
    }

    @Path("/{id}"+EP_FOOTPRINTS)
    public FootprintsResource getFootprints(@Context ContainerRequest ctx,
                                            @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(FootprintsResource.class, c.account);
    }

    @Path("/{id}"+EP_NETWORKS)
    public NetworksResource getNetworks(@Context ContainerRequest ctx,
                                        @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(NetworksResource.class, c.account);
    }

    @Path("/{id}"+EP_NODES)
    public NodesResource getNodes(@Context ContainerRequest ctx,
                                  @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(NodesResource.class, c.account);
    }

    @Path("/{id}"+EP_DEVICES)
    public DevicesResource getDevices(@Context ContainerRequest ctx,
                                      @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(DevicesResource.class, c.account);
    }

    @Path("/{id}"+EP_ROLES)
    public AnsibleRolesResource getAnsibleRoles(@Context ContainerRequest ctx,
                                                @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(AnsibleRolesResource.class, c.account);
    }

    @Path("/{id}"+EP_VPN)
    public VpnConfigResource getVpnConfig(@Context ContainerRequest ctx,
                                                @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(VpnConfigResource.class, c.account);
    }

    @Path("/{id}"+EP_PLANS)
    public AccountPlansResource getPlans(@Context ContainerRequest ctx,
                                         @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(AccountPlansResource.class, c.account);
    }

    @Path("/{id}"+EP_KEYS)
    public AccountSshKeysResource getSshKeys(@Context ContainerRequest ctx,
                                             @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(AccountSshKeysResource.class, c.account);
    }

    @Path("/{id}"+EP_PAYMENT_METHODS)
    public AccountPaymentMethodsResource getAccountPaymentMethods(@Context ContainerRequest ctx,
                                                                  @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(AccountPaymentMethodsResource.class, c.account);
    }

    @Path("/{id}"+EP_BILLS)
    public BillsResource getBills(@Context ContainerRequest ctx,
                                  @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(BillsResource.class, c.account);
    }

    @Path("/{id}"+EP_PAYMENTS)
    public AccountPaymentsResource getPayments(@Context ContainerRequest ctx,
                                               @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(AccountPaymentsResource.class, c.account);
    }

    @Path("/{id}"+EP_SENT_NOTIFICATIONS)
    public SentNotificationsResource getSentNotificationsResource(@Context ContainerRequest ctx,
                                                                  @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(SentNotificationsResource.class, c.account);
    }

    @Path("/{id}"+EP_RECEIVED_NOTIFICATIONS)
    public ReceivedNotificationsResource getReceivedNotificationsResource(@Context ContainerRequest ctx,
                                                                  @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(ReceivedNotificationsResource.class, c.account);
    }

    @Path("/{id}"+EP_REFERRAL_CODES)
    public ReferralCodesResource getReferralCodesResource(@Context ContainerRequest ctx,
                                                          @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        return configuration.subResource(ReferralCodesResource.class, c.account);
    }

    // Non-admins can only read/edit/delete themselves. Admins can do anything to anyone.
    private class AccountContext {
        public Account caller;
        public Account account;
        public ContainerRequest ctx;
        public String id;
        public AccountContext (ContainerRequest ctx, String id, boolean okNotFound) {
            this.ctx = ctx;
            this.caller = userPrincipal(ctx);
            this.id = id;
            if (id != null) {
                account = accountDAO.findById(id);
                if (account == null) {
                    if (okNotFound) return;
                    if (caller.admin()) throw notFoundEx(id);
                    throw forbiddenEx();
                }
                if (!account.getUuid().equals(caller.getUuid()) && !caller.admin()) throw forbiddenEx();
            } else {
                if (!caller.admin()) throw forbiddenEx();
            }
        }
        public AccountContext (ContainerRequest ctx, String id) {
            this(ctx, id, false);
        }
        public AccountContext (ContainerRequest ctx) {
            this(ctx, null, false);
        }
    }

}
