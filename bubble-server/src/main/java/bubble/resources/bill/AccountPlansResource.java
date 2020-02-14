package bubble.resources.bill;

import bubble.cloud.CloudServiceType;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.cloud.payment.PaymentServiceDriver;
import bubble.cloud.payment.PromotionalPaymentServiceDriver;
import bubble.dao.account.AccountSshKeyDAO;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.bill.PromotionDAO;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleFootprintDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountSshKey;
import bubble.model.bill.*;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleFootprint;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import bubble.resources.account.AccountOwnedResource;
import bubble.server.BubbleConfiguration;
import bubble.service.account.AuthenticatorService;
import bubble.service.cloud.GeoService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

import static bubble.ApiConstants.*;
import static bubble.model.cloud.BubbleNetwork.validateHostname;
import static org.cobbzilla.util.string.ValidationRegexes.HOST_PATTERN;
import static org.cobbzilla.util.string.ValidationRegexes.validateRegexMatches;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public class AccountPlansResource extends AccountOwnedResource<AccountPlan, AccountPlanDAO> {

    @Autowired private AccountSshKeyDAO sshKeyDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private BubblePlanDAO planDAO;
    @Autowired private BubbleFootprintDAO footprintDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private AuthenticatorService authenticatorService;
    @Autowired private GeoService geoService;
    @Autowired private PromotionDAO promotionDAO;

    public AccountPlansResource(Account account) { super(account); }

    @Override protected List<AccountPlan> list(ContainerRequest ctx) {
        return getDao().findByAccountAndNotDeleted(account.getUuid());
    }

    @Override protected AccountPlan findAlternate(ContainerRequest ctx, String id) {
        // id might be a network uuid
        final String accountUuid = getAccountUuid(ctx);
        final BubbleNetwork network = networkDAO.findByAccountAndId(accountUuid, id);
        return network == null ? null : getDao().findByAccountAndNetwork(accountUuid, network.getUuid());
    }

    @Override protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, AccountPlan request) {
        authenticatorService.ensureAuthenticated(ctx);

        // ensure caller is not from a disallowed country
        if (configuration.hasDisallowedCountries()) {
            // do we have a geoLocation service?
            final List<CloudService> geoLocationServices = cloudDAO.findByAccountAndType(request.getAccount(), CloudServiceType.geoLocation);
            final String remoteHost = getRemoteHost(req);
            for (CloudService geo : geoLocationServices) {
                try {
                    final GeoLocation location = geoService.locate(request.getAccount(), remoteHost);
                    if (configuration.isDisallowed(location.getCountry())) throw invalidEx("err.accountPlan.callerCountryDisallowed");
                    break;
                } catch (Exception e) {
                    log.debug("canCreate: error geo-locating address "+remoteHost+": "+e);
                }
            }
        }
        return super.canCreate(req, ctx, caller, request);
    }

    @Override protected boolean canUpdate(ContainerRequest ctx, Account caller, AccountPlan found, AccountPlan request) {
        authenticatorService.ensureAuthenticated(ctx);
        return super.canUpdate(ctx, caller, found, request);
    }

    @Override protected boolean canDelete(ContainerRequest ctx, Account caller, AccountPlan found) {
        authenticatorService.ensureAuthenticated(ctx);
        return super.canDelete(ctx, caller, found);
    }

    @Override protected AccountPlan setReferences(ContainerRequest ctx, Account caller, AccountPlan request) {

        final ValidationResult errors = new ValidationResult();
        if (!request.hasTimezone()) errors.addViolation("err.timezone.required");
        if (!request.hasLocale()) errors.addViolation("err.locale.required");

        if (request.hasSshKey()) {
            final AccountSshKey sshKey = sshKeyDAO.findByAccountAndId(caller.getUuid(), request.getSshKey());
            if (sshKey == null) {
                errors.addViolation("err.sshPublicKey.notFound");
            } else {
                request.setSshKey(sshKey.getUuid());
            }
        } else {
            request.setSshKey(null); // if it's an empty string, make it null (see simple_network test)
        }

        final BubbleDomain domain = domainDAO.findByAccountAndId(caller.getUuid(), request.getDomain());
        if (domain == null) {
            log.info("setReferences: domain not found: "+request.getDomain()+" for caller: "+caller.getUuid());
            errors.addViolation("err.domain.required");
        } else {
            request.setDomain(domain.getUuid());

            final BubbleNetwork existingNetwork = networkDAO.findByNameAndDomainName(request.getName(), domain.getName());
            if (existingNetwork != null) errors.addViolation("err.name.networkNameAlreadyExists");
        }

        if (request.hasForkHost()) {
            if (!configuration.isSageLauncher()) {
                errors.addViolation("err.forkHost.notAllowed");
            } else {
                final String forkHost = request.getForkHost();
                if (!validateRegexMatches(HOST_PATTERN, forkHost)) {
                    errors.addViolation("err.forkHost.invalid");
                } else if (domain != null && !forkHost.endsWith("."+domain.getName())) {
                    errors.addViolation("err.forkHost.domainMismatch");
                } else if (domain != null) {
                    request.setName(domain.networkFromFqdn(forkHost, errors));
                    validateHostname(request, errors);
                }
            }
        } else {
            validateHostname(request, errors);
        }

        final BubblePlan plan = planDAO.findByAccountOrParentAndId(caller, request.getPlan());
        if (plan == null) {
            errors.addViolation("err.plan.required");
        } else {
            request.setPlan(plan.getUuid());
        }

        final BubbleNetwork network = networkDAO.findByAccountAndId(caller.getUuid(), request.getNetwork());
        if (network != null) {
            errors.addViolation("err.network.exists", "A plan already exists for this network", request.getNetwork());
        }

        final CloudService storage = selectStorageCloud(ctx, caller, request, errors);

        if (request.hasFootprint()) {
            final BubbleFootprint footprint = footprintDAO.findByAccountAndId(caller.getUuid(), request.getFootprint());
            if (footprint == null) {
                errors.addViolation("err.footprint.required");
            } else {
                request.setFootprint(footprint.getUuid());
            }
        } else {
            log.warn("setReferences: footprint not set, using default");
            request.setFootprint(configuration.getThisNetwork().getFootprint());
        }

        AccountPaymentMethod paymentMethod = null;
        if (configuration.paymentsEnabled()) {
            if (!request.hasPaymentMethodObject()) {
                errors.addViolation("err.paymentMethod.required");
            } else {
                if (request.getPaymentMethodObject().hasUuid()) {
                    paymentMethod = paymentMethodDAO.findByUuid(request.getPaymentMethodObject().getUuid());
                    if (paymentMethod == null) errors.addViolation("err.purchase.paymentMethodNotFound");
                } else {
                    paymentMethod = request.getPaymentMethodObject();
                }
                if (paymentMethod != null) {
                    paymentMethod.setAccount(caller.getUuid()).validate(errors, configuration);
                }
            }
            if (request.hasReferralFrom()) {
                final Account referredFrom = accountDAO.findByName(request.getReferralFrom());
                if (referredFrom == null || referredFrom.deleted()) {
                    errors.addViolation("err.referralFrom.invalid");
                }
                // check for referral promotion
                final Promotion referralPromo = promotionDAO.findReferralPromotion();
                if (referralPromo == null) {
                    errors.addViolation("err.referralFrom.unavailable");
                } else {
                    final CloudService referralCloud = cloudDAO.findByUuid(referralPromo.getCloud());
                    if (referralCloud == null || referralCloud.getType() != CloudServiceType.payment) {
                        errors.addViolation("err.referralFrom.configurationError");
                    } else {
                        final PaymentServiceDriver referralDriver = referralCloud.getPaymentDriver(configuration);
                        if (referralDriver.getPaymentMethodType() != PaymentMethodType.promotional_credit
                                || !(referralDriver instanceof PromotionalPaymentServiceDriver)) {
                            errors.addViolation("err.referralFrom.configurationError");
                        } else {
                            final PromotionalPaymentServiceDriver promoDriver = (PromotionalPaymentServiceDriver) referralDriver;
                            promoDriver.applyReferralPromo(referralPromo, caller, referredFrom);
                        }
                    }
                }
            }
            final Promotion promo;
            if (request.hasPromoCode()) {
                promo = promotionDAO.findEnabledWithCode(request.getPromoCode());
                if (promo == null) {
                    errors.addViolation("err.promoCode.notFound");
                } else if (promo.inactive()) {
                    errors.addViolation("err.promoCode.notActive");
                }
            } else {
                promo = promotionDAO.findEnabledWithNoCode();
            }
            if (promo != null) {
                final CloudService referralCloud = cloudDAO.findByUuid(promo.getCloud());
                if (referralCloud == null || referralCloud.getType() != CloudServiceType.payment) {
                    errors.addViolation("err.promoCode.configurationError");
                } else {
                    final PaymentServiceDriver referralDriver = referralCloud.getPaymentDriver(configuration);
                    if (referralDriver.getPaymentMethodType() != PaymentMethodType.promotional_credit
                            || !(referralDriver instanceof PromotionalPaymentServiceDriver)) {
                        errors.addViolation("err.promoCode.configurationError");
                    } else {
                        final PromotionalPaymentServiceDriver promoDriver = (PromotionalPaymentServiceDriver) referralDriver;
                        promoDriver.applyPromo(promo, caller);
                    }
                }
            }
        }
        if (errors.isInvalid()) throw invalidEx(errors);

        if (domain != null && plan != null && storage != null) {
            final BubbleNetwork newNetwork = networkDAO.create(request.bubbleNetwork(caller, domain, plan, storage));
            request.setNetwork(newNetwork.getUuid());
        }

        if (configuration.paymentsEnabled()) {
            if (paymentMethod != null && !paymentMethod.hasUuid()) {
                final AccountPaymentMethod paymentMethodToCreate = new AccountPaymentMethod(request.getPaymentMethodObject()).setAccount(request.getAccount());
                final AccountPaymentMethod paymentMethodCreated = paymentMethodDAO.create(paymentMethodToCreate);
                request.setPaymentMethodObject(paymentMethodCreated);
            } else {
                request.setPaymentMethodObject(paymentMethod);
            }
        }

        return request;
    }

    private CloudService selectStorageCloud(ContainerRequest ctx, Account caller, AccountPlan request, ValidationResult result) {
        final List<CloudService> storageClouds = cloudDAO.findByAccountAndType(caller.getUuid(), CloudServiceType.storage);

        // find the first one that is not LocalStorage
        final List<CloudService> remoteStorage = storageClouds.stream().filter(CloudService::isNotLocalStorage).collect(Collectors.toList());

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

    // If the accountPlan is not found, look for an orphaned network
    @DELETE @Path("/{id}")
    @Override public Response delete(@Context ContainerRequest ctx,
                                     @PathParam("id") String id) {
        final Account caller = checkEditable(ctx);
        AccountPlan found = find(ctx, id);
        if (found == null) {
            found = findAlternateForDelete(ctx, id);
            if (found == null) {
                final BubbleNetwork orphan = networkDAO.findByAccountAndId(getAccountUuid(ctx), id);
                if (orphan == null) return notFound(id);
                log.warn("delete: deleting orphaned network: "+orphan.getUuid()+"/"+orphan.getNetworkDomain());
                networkDAO.delete(orphan.getUuid());
                return ok_empty();
            }
        }

        if (!canDelete(ctx, caller, found)) return forbidden();

        getDao().delete(found.getUuid());
        return ok(found);
    }

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
        final AccountPlan accountPlan = find(ctx, id);
        if (accountPlan == null) return notFound(id);

        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(accountPlan.getPaymentMethod());
        return paymentMethod == null ? notFound() : ok(paymentMethod);
    }

    @GET @Path("/{id}"+EP_PLAN)
    public Response getBubblePlan(@Context ContainerRequest ctx,
                                  @PathParam("id") String id) {
        final AccountPlan accountPlan = find(ctx, id);
        if (accountPlan == null) return notFound(id);

        final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
        return plan == null ? notFound() : ok(plan);
    }

    @Path("/{id}"+EP_APPS)
    public BubblePlanAppsResource getApps(@Context ContainerRequest ctx,
                                          @PathParam("id") String id) {
        final AccountPlan accountPlan = find(ctx, id);
        if (accountPlan == null) throw notFoundEx(id);
        final Account account = accountDAO.findByUuid(getAccountUuid(ctx));
        final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
        return configuration.subResource(BubblePlanAppsResource.class, account, plan);
    }

}
