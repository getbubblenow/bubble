package bubble.resources.account;

import bubble.cloud.CloudServiceType;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.account.message.AccountMessageDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.AccountPolicy;
import bubble.model.account.AccountRegistration;
import bubble.model.account.message.AccountAction;
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
import bubble.service.account.download.AccountDownloadService;
import bubble.service.cloud.StandardNetworkService;
import lombok.extern.slf4j.Slf4j;
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
        if (c.account != null) return invalid("err.user.exists", "User with name "+request.getName()+" already exists", request.getName());
        if (!request.hasContact()) return invalid("err.user.noContact", "No contact information provided", request.getName());

        final String parentUuid = request.hasParent() ? request.getParent() : configuration.getThisNetwork().getAccount();
        final Account parent = parentUuid.equalsIgnoreCase(c.caller.getUuid()) ? c.caller : accountDAO.findByUuid(parentUuid);
        if (parent == null) return invalid("err.parent.notFound", "Parent account does not exist: "+parentUuid);

        final AccountRegistration reg = (AccountRegistration) request
                .setRemoteHost(getRemoteHost(req))
                .setVerifyContact(true);
        final Account created = accountDAO.newAccount(req, reg, parent);
        return ok(created.waitForAccountInit());
    }

    @GET @Path("/{id}"+EP_DOWNLOAD)
    public Response downloadAllUserData(@Context Request req,
                                        @Context ContainerRequest ctx,
                                        @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        if (!c.account.admin()) return forbidden();
        final Map<String, List<String>> data = downloadService.downloadAccountData(req, id, false);
        return data != null ? ok(data) : invalid("err.download.error");
    }

    @POST @Path("/{id}")
    public Response updateUser(@Context ContainerRequest ctx,
                               @PathParam("id") String id,
                               Account request) {
        final AccountContext c = new AccountContext(ctx, id);
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

    @GET @Path("/{id}"+EP_POLICY)
    public Response viewPolicy(@Context ContainerRequest ctx,
                               @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());
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

        final AccountContact existing = policy.findContact(contact);
        if (existing != null) {
            if (existing.isAuthenticator() && (!contact.hasUuid() || !existing.getUuid().equals(contact.getUuid()))) {
                return invalid("err.authenticator.configured");
            }

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
        return ok(added);
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
        return ok(policy);
    }

    @GET @Path("/{id}"+EP_POLICY+EP_CONTACTS+"/{type}/{info}")
    public Response findContact(@Context ContainerRequest ctx,
                                @PathParam("id") String id,
                                @PathParam("type") CloudServiceType type,
                                @PathParam("info") String info) {
        final AccountContext c = new AccountContext(ctx, id);
        final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());
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
        final AccountContact contact = policy.findContact(new AccountContact().setType(type).setInfo(info));
        if (contact == null) return notFound(type.name()+"/"+info);
        return ok(policyDAO.update(policy.removeContact(contact)).mask());
    }

    @DELETE @Path("/{id}"+EP_POLICY+EP_CONTACTS+EP_AUTHENTICATOR)
    public Response removeAuthenticator(@Context ContainerRequest ctx,
                                        @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());
        final AccountContact contact = policy.findContact(new AccountContact().setType(CloudServiceType.authenticator));
        if (contact == null) return notFound(CloudServiceType.authenticator.name());
        return ok(policyDAO.update(policy.removeContact(contact)).mask());
    }

    @DELETE @Path("/{id}"+EP_POLICY+EP_CONTACTS+"/{uuid}")
    public Response removeContactByUuid(@Context ContainerRequest ctx,
                                        @PathParam("id") String id,
                                        @PathParam("uuid") String uuid) {
        final AccountContext c = new AccountContext(ctx, id);
        final AccountPolicy policy = policyDAO.findSingleByAccount(c.account.getUuid());
        final AccountContact found = policy.findContact(new AccountContact().setUuid(uuid));
        if (found == null) return notFound(uuid);
        return ok(policyDAO.update(policy.removeContact(found)).mask());
    }

    @DELETE @Path("/{id}"+EP_REQUEST)
    public Response requestDeleteUser(@Context Request req,
                                      @Context ContainerRequest ctx,
                                      @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);

        // request deletion
        return ok(messageDAO.create(new AccountMessage()
                .setMessageType(AccountMessageType.request)
                .setAction(AccountAction.delete)
                .setTarget(ActionTarget.account)
                .setAccount(c.account.getUuid())
                .setName(c.account.getUuid())
                .setRemoteHost(getRemoteHost(req))));
    }

    @DELETE @Path("/{id}")
    public Response rootDeleteUser(@Context ContainerRequest ctx,
                                   @PathParam("id") String id) {
        final AccountContext c = new AccountContext(ctx, id);
        if (!c.caller.admin()) return forbidden();
        if (c.caller.getUuid().equals(c.account.getUuid())) {
            return invalid("err.delete.cannotDeleteSelf");
        }
        accountDAO.delete(c.account.getUuid());
        return ok(c.account);
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
