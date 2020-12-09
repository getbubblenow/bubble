/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.bill;

import bubble.cloud.CloudServiceType;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.dao.account.AccountSshKeyDAO;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleFootprintDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountSshKey;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.BubblePlan;
import bubble.model.bill.PaymentMethodType;
import bubble.model.cloud.*;
import bubble.resources.account.AccountOwnedResource;
import bubble.server.BubbleConfiguration;
import bubble.service.account.StandardAuthenticatorService;
import bubble.service.cloud.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import static bubble.model.account.Account.ROOT_EMAIL;
import static bubble.model.cloud.BubbleNetwork.validateHostname;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_NOT_FOUND;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.util.string.ValidationRegexes.*;
import static org.cobbzilla.wizard.model.NamedEntity.NAME_MAXLEN;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Slf4j
public class AccountPlansResource extends AccountOwnedResource<AccountPlan, AccountPlanDAO> {

    @Autowired private AccountSshKeyDAO sshKeyDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired protected BubbleNetworkDAO networkDAO;
    @Autowired private BubblePlanDAO planDAO;
    @Autowired private BubbleFootprintDAO footprintDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private StandardAuthenticatorService authenticatorService;
    @Autowired private GeoService geoService;

    public AccountPlansResource(Account account) { super(account); }

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
            if (!empty(geoLocationServices)) {
                try {
                    final GeoLocation location = geoService.locate(request.getAccount(), remoteHost);
                    if (configuration.isDisallowed(location.getCountry())) throw invalidEx("err.accountPlan.callerCountryDisallowed");
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

    @Override protected Object daoCreate(AccountPlan toCreate) {
        if (!configuration.getPaymentsEnabled()) toCreate.setEnabled(true);
        return super.daoCreate(toCreate);
    }

    @Override protected Object daoUpdate(AccountPlan toUpdate) {
        if (!configuration.getPaymentsEnabled()) toUpdate.setEnabled(true);
        return super.daoUpdate(toUpdate);
    }

    @Override protected AccountPlan setReferences(ContainerRequest ctx, Request req, Account caller, AccountPlan request) {

        // ensure we have latest account settings (preferredPlan/etc)
        caller = accountDAO.findByUuid(caller.getUuid());

        final ValidationResult errors = new ValidationResult();
        if (!request.hasTimezone() || request.getTimezone().equals(DETECT_TIMEZONE)) {
            request.setTimezone(geoService.getTimeZone(caller, getRemoteHost(req)).getTimeZoneId());
        }
        if (!request.hasLocale() || request.getLocale().equals(DETECT_LOCALE)) {
            request.setLocale(geoService.getFirstLocale(account, getRemoteHost(req), normalizeLangHeader(req)));
        }
        request.setAccount(caller.getUuid());

        if (request.hasSshKey()) {
            final AccountSshKey sshKey = sshKeyDAO.findByAccountAndId(caller.getUuid(), request.getSshKey());
            if (sshKey == null) {
                errors.addViolation("err.sshPublicKey.notFound");
            } else {
                request.setSshKey(sshKey.getUuid());
            }
        } else if (configuration.isSageLauncher()) {
            final List<AccountSshKey> sshKeys = sshKeyDAO.findByAccount(caller.getUuid());
            if (empty(sshKeys)) {
                request.setSshKey(null);
            } else {
                request.setSshKey(sshKeys.get(0).getUuid());
            }
        } else {
            request.setSshKey(null); // if it's an empty string, make it null (see simple_network test)
        }

        BubbleDomain domain = domainDAO.findByAccountAndId(caller.getUuid(), request.getDomain());
        if (domain == null) {
            final List<BubbleDomain> domains = domainDAO.findByAccount(caller.getUuid());
            if (empty(domains)) return die("setReferences: no domains found for account: "+caller.getUuid());
            domain = domains.get(0);
            request.setDomain(domain.getUuid());
        } else {
            request.setDomain(domain.getUuid());
            final BubbleNetwork existingNetwork = networkDAO.findByNameAndDomainName(request.getName(), domain.getName());
            if (existingNetwork != null) errors.addViolation("err.name.networkNameAlreadyExists");
        }

        if (request.hasLaunchType() && request.getLaunchType().fork()) {
            if (!configuration.isSageLauncher()) {
                errors.addViolation("err.forkHost.notAllowed");
            } else {
                final String forkHost = request.getForkHost();
                if (empty(forkHost)) {
                    request.setName(newNetworkName());
                    request.setDomain(domain.getUuid());

                } else {
                    if (!validateRegexMatches(HOST_PATTERN, forkHost)) {
                        errors.addViolation("err.forkHost.invalid");
                    } else if (domain != null && !forkHost.endsWith("." + domain.getName())) {
                        final BubbleDomain foundDomain = domainDAO.findByAccount(caller.getUuid()).stream()
                                .filter(d -> forkHost.equals("." + d.getName()))
                                .findFirst().orElse(null);
                        if (foundDomain == null) {
                            errors.addViolation("err.forkHost.domainNotFound");
                        } else {
                            request.setDomain(foundDomain.getUuid());
                        }

                    } else if (domain != null) {
                        request.setName(domain.networkFromFqdn(forkHost, errors));
                        validateName(request, errors);
                    }
                }
            }
            final String adminEmail = request.getAdminEmail();
            if (request.hasLaunchType() && request.getLaunchType().fork() && caller.admin()) {
                if (empty(adminEmail) && caller.getEmail().equals(ROOT_EMAIL)) {
                    errors.addViolation("err.adminEmail.required");
                } else if (!empty(adminEmail) && !validateRegexMatches(EMAIL_PATTERN, adminEmail)) {
                    errors.addViolation("err.adminEmail.invalid");
                }
            } else {
                if (!empty(adminEmail)) {
                    errors.addViolation("err.adminEmail.cannotSet");
                }
            }
        } else {
            if (!request.hasNickname()) {
                if (!request.hasName()) request.setName(newNetworkName());
                request.setNickname(request.getName());
            }
            if (request.hasNickname() && request.getNickname().length() > NAME_MAXLEN) {
                errors.addViolation("err.name.tooLong");
            }
            // assign a random name for the network
            request.setName(newNetworkName());
        }
        log.info("setReferences: after calling validateName, request.name="+request.getName());

        BubblePlan plan = planDAO.findByAccountOrParentAndId(caller, request.getPlan());
        if (plan == null) {
            plan = planDAO.findByUuid(caller.getPreferredPlan());
            if (plan == null) {
                final List<BubblePlan> bubblePlans = planDAO.findAll();
                if (empty(bubblePlans)) {
                    errors.addViolation("err.plan.required");
                } else {
                    plan = bubblePlans.get(0);
                    request.setPlan(plan.getUuid());
                }
            } else {
                request.setPlan(plan.getUuid());
            }
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
            final BubbleFootprint footprint = footprintDAO.findByAccountAndNameAndParentId(caller, configuration.getThisNetwork().getFootprint());
            if (footprint == null) {
                errors.addViolation("err.footprint.defaultNotFound");
            } else {
                request.setFootprint(footprint.getUuid());
            }
        }

        AccountPaymentMethod paymentMethod = null;
        if (configuration.getPaymentsEnabled()) {
            if (!request.hasPaymentMethodObject()) {
                final List<AccountPaymentMethod> paymentMethods = paymentMethodDAO.findByAccountAndNotPromoAndNotDeleted(caller.getUuid());
                if (empty(paymentMethods)) {
                    errors.addViolation("err.paymentMethod.required");
                } else {
                    request.setPaymentMethodObject(paymentMethods.get(0));
                }
            }
            if (request.hasPaymentMethodObject() && request.getPaymentMethodObject().hasUuid()) {
                paymentMethod = paymentMethodDAO.findByUuid(request.getPaymentMethodObject().getUuid());
                if (paymentMethod == null) errors.addViolation("err.purchase.paymentMethodNotFound");
            } else {
                paymentMethod = request.getPaymentMethodObject();
            }
            if (paymentMethod != null && plan != null) {
                if (paymentMethod.hasPromotion() || paymentMethod.getPaymentMethodType() == PaymentMethodType.promotional_credit) {
                    // cannot pay with a promo credit, must supply another payment method.
                    // promos will be applied at purchase, and may result in no charge to this payment method
                    errors.addViolation("err.purchase.paymentMethodNotFound");
                } else {
                    paymentMethod.setAccount(caller.getUuid()).validate(errors, configuration);
                }
            }
        }
        if (errors.isInvalid()) throw invalidEx(errors);

        if (domain != null && plan != null && storage != null) {
            if (!request.sendMetrics() && configuration.requireSendMetrics()) {
                throw invalidEx("err.sendMetrics.cannotDisable");
            }
            if (!request.sendErrors() && configuration.requireSendMetrics()) {
                throw invalidEx("err.sendErrors.cannotDisable");
            }
            final BubbleNetwork newNetwork = networkDAO.create(request.bubbleNetwork(caller, domain, plan, storage));
            request.setNetwork(newNetwork.getUuid());
        }

        if (configuration.getPaymentsEnabled()) {
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

    private void validateName(AccountPlan request, ValidationResult errors) {
        final HostnameValidationResult hostnameErrors = validateHostname(request, accountDAO, networkDAO);
        if (hostnameErrors.isInvalid()) {
            errors.addAll(hostnameErrors);
        } else if (hostnameErrors.hasSuggestedName()) {
            request.setName(hostnameErrors.getSuggestedName());
        }
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
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_ACCOUNT,
            summary="Delete AccountPlan",
            description="Delete AccountPlan. If a Bubble is running on the plan, it will be stopped.",
            parameters=@Parameter(name="id", description="uuid of the AccountPlan to delete", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="the deleted AccountPlan")
    )
    @Override public Response delete(@Context Request req,
                                     @Context ContainerRequest ctx,
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

        getDao().update(found.setDeleting(true));

        final String planUuid = found.getUuid();
        background(() -> getDao().delete(planUuid), "AccountPlansResource.delete");

        return ok(found.setDeletedNetwork(found.getNetwork()));
    }

    @Path("/{id}"+EP_BILLS)
    public BillsResource getBills(@Context ContainerRequest ctx,
                                  @PathParam("id") String id) {
        configuration.requiresPaymentsEnabled();
        final AccountPlan plan = find(ctx, id);
        if (plan == null) throw notFoundEx(id);
        return configuration.subResource(BillsResource.class, account, plan);
    }

    @Path("/{id}"+EP_PAYMENTS)
    public AccountPaymentsResource getPayments(@Context ContainerRequest ctx,
                                               @PathParam("id") String id) {
        configuration.requiresPaymentsEnabled();
        final AccountPlan plan = find(ctx, id);
        if (plan == null) throw notFoundEx(id);
        return configuration.subResource(AccountPaymentsResource.class, account, plan);
    }

    @GET @Path("/{id}"+EP_PAYMENT_METHOD)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_PAYMENT,
            summary="Get payment method for plan",
            description="Get the AccountPaymentMethod associated with the Account Plan",
            parameters=@Parameter(name="id", description="UUID or name of AccountPlan", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="an AccountPaymentMethod object"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="account plan not found")
            }
    )
    public Response getPaymentMethod(@Context ContainerRequest ctx,
                                     @PathParam("id") String id) {
        configuration.requiresPaymentsEnabled();
        final AccountPlan accountPlan = find(ctx, id);
        if (accountPlan == null) return notFound(id);

        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(accountPlan.getPaymentMethod());
        return paymentMethod == null ? notFound() : ok(paymentMethod);
    }

    @GET @Path("/{id}"+EP_PLAN)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_ACCOUNT,
            summary="Get Bubble Plan",
            description="Get Bubble Plan for Account Plan",
            parameters=@Parameter(name="id", description="UUID or name of AccountPlan", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="a BubblePlan object"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="account plan not found")
            }
    )
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
