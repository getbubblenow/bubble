package bubble.resources.bill;

import bubble.cloud.CloudServiceType;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.AccountPlanPaymentMethodDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleFootprintDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.AccountPlanPaymentMethod;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleFootprint;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import bubble.resources.account.AccountOwnedResource;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

import static bubble.ApiConstants.*;
import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public class AccountPlansResource extends AccountOwnedResource<AccountPlan, AccountPlanDAO> {

    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private BubblePlanDAO planDAO;
    @Autowired private BubbleFootprintDAO footprintDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private AccountPaymentMethodDAO accountPaymentMethodDAO;
    @Autowired private AccountPlanPaymentMethodDAO accountPlanPaymentMethodDAO;
    @Autowired private BubbleConfiguration configuration;

    public AccountPlansResource(Account account) { super(account); }

    @Path("/{id}"+EP_BILLS)
    public BillsResource getBills(@Context ContainerRequest ctx,
                                  @PathParam("id") String id) {
        final AccountPlan plan = find(ctx, id);
        if (plan == null) throw notFoundEx(id);
        return configuration.subResource(BillsResource.class, account, plan);
    }

    @Path("/{id}"+EP_PAYMENTS)
    public AccountPaymentsResource getPayments(@Context ContainerRequest ctx,
                                               @PathParam("id") String id) {
        final AccountPlan plan = find(ctx, id);
        if (plan == null) throw notFoundEx(id);
        return configuration.subResource(AccountPaymentsResource.class, account, plan);
    }

    @GET @Path("/{id}"+EP_PAYMENT_METHOD)
    public Response getPaymentMethod(@Context ContainerRequest ctx,
                                     @PathParam("id") String id) {
        final AccountPlan plan = find(ctx, id);
        if (plan == null) return notFound(id);

        final AccountPlanPaymentMethod planPaymentMethod = accountPlanPaymentMethodDAO.findCurrentMethodForPlan(plan.getUuid());
        if (planPaymentMethod == null) return notFound();

        final AccountPaymentMethod paymentMethod = accountPaymentMethodDAO.findByUuid(planPaymentMethod.getPaymentMethod());
        return paymentMethod == null ? notFound() : ok(paymentMethod);
    }

    @Override protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, AccountPlan request) {
        // ensure caller is not from a disallowed country
        if (configuration.hasDisallowedCountries()) {
            // do we have a geoLocation service?
            final List<CloudService> geoLocationServices = cloudDAO.findByAccountAndType(request.getAccount(), CloudServiceType.geoLocation);
            final String remoteHost = getRemoteHost(req);
            for (CloudService geo : geoLocationServices) {
                try {
                    final GeoLocation location = geo.getGeoLocateDriver(configuration).geolocate(remoteHost);
                    if (configuration.isDisallowed(location.getCountry())) throw invalidEx("err.accountPlan.callerCountryDisallowed");
                    break;
                } catch (Exception e) {
                    log.debug("canCreate: error geo-locating address "+remoteHost+": "+e);
                }
            }
        }
        return super.canCreate(req, ctx, caller, request);
    }

    @Override protected AccountPlan setReferences(ContainerRequest ctx, Account caller, AccountPlan request) {

        final ValidationResult result = new ValidationResult();
        if (!request.hasTimezone()) result.addViolation("err.timezone.required");
        if (!request.hasLocale()) result.addViolation("err.locale.required");

        final BubbleDomain domain = domainDAO.findByAccountAndId(caller.getUuid(), request.getDomain());
        if (domain == null) {
            log.info("setReferences: domain not found: "+request.getDomain()+" for caller: "+caller.getUuid());
            result.addViolation("err.domain.required");
        } else {
            request.setDomain(domain.getUuid());
        }

        final BubblePlan plan = planDAO.findByAccountOrParentAndId(caller, request.getPlan());
        if (plan == null) {
            result.addViolation("err.plan.required");
        } else {
            request.setPlan(plan.getUuid());
        }

        final BubbleNetwork network = networkDAO.findByAccountAndId(caller.getUuid(), request.getNetwork());
        if (network != null) {
            result.addViolation("err.network.exists", "A plan already exists for this network", request.getNetwork());
        }

        final CloudService storage = selectStorageCloud(ctx, caller, request, result);

        if (request.hasFootprint()) {
            final BubbleFootprint footprint = footprintDAO.findByAccountAndId(caller.getUuid(), request.getFootprint());
            if (footprint == null) {
                result.addViolation("err.footprint.required");
            } else {
                request.setFootprint(footprint.getUuid());
            }
        } else {
            log.warn("setReferences: footprint not set, using default");
            request.setFootprint(configuration.getThisNetwork().getFootprint());
        }

        AccountPaymentMethod paymentMethod = null;
        if (!request.hasPaymentMethod()) {
            result.addViolation("err.paymentMethod.required");
        } else {
            if (request.getPaymentMethod().hasUuid()) {
                paymentMethod = accountPaymentMethodDAO.findByUuid(request.getPaymentMethod().getUuid());
                if (paymentMethod == null) result.addViolation("err.purchase.paymentMethodNotFound");
            } else {
                paymentMethod = request.getPaymentMethod();
            }
            if (paymentMethod != null) {
                paymentMethod.setAccount(caller.getUuid()).validate(result, configuration);
            }
        }

        if (result.isInvalid()) throw invalidEx(result);

        if (domain != null && plan != null && storage != null) {
            final BubbleNetwork newNetwork = networkDAO.create(request.bubbleNetwork(caller, domain, plan, storage));
            request.setNetwork(newNetwork.getUuid());
        }
        if (paymentMethod != null && !paymentMethod.hasUuid()) {
            final AccountPaymentMethod paymentMethodToCreate = new AccountPaymentMethod(request.getPaymentMethod()).setAccount(request.getAccount());
            final AccountPaymentMethod paymentMethodCreated = accountPaymentMethodDAO.create(paymentMethodToCreate);
            request.setPaymentMethod(paymentMethodCreated);
        }
        return request;
    }

    private CloudService selectStorageCloud(ContainerRequest ctx, Account caller, AccountPlan request, ValidationResult result) {
        final List<CloudService> storageClouds = cloudDAO.findByAccountAndType(caller.getUuid(), CloudServiceType.storage);

        // find the first one that is not LocalStorage
        final List<CloudService> remoteStorage = storageClouds.stream().filter(c -> !c.getName().equals(LOCAL_STORAGE)).collect(Collectors.toList());

        if (!remoteStorage.isEmpty()) {
            // todo: storage should know what region it is in.
            // translate that to lat/lon, and choose storage that is closest to the compute region in use.
            return remoteStorage.get(0);

        } else if (!storageClouds.isEmpty()) {
            // we only have LocalStorage. Oh well, use it.
            return storageClouds.get(0);

        } else {
            result.addViolation("err.storage.unavailable");
            return null;
        }
    }

}
