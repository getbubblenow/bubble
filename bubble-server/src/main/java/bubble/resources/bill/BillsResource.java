/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.bill;

import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.bill.*;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.bill.*;
import bubble.model.cloud.CloudService;
import bubble.resources.account.ReadOnlyAccountOwnedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationEvictionPolicy;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.util.http.URIUtil.queryParams;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Slf4j
public class BillsResource extends ReadOnlyAccountOwnedResource<Bill, BillDAO> {

    public static final String PARAM_PAYMENTS = "payments";

    @Autowired private BubblePlanDAO planDAO;
    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired private AccountPaymentDAO paymentDAO;
    @Autowired private CloudServiceDAO cloudDAO;

    private AccountPlan accountPlan;

    public BillsResource(Account account) { super(account); }

    public BillsResource(Account account, AccountPlan accountPlan) {
        super(account);
        this.accountPlan = accountPlan;
    }

    @Override protected Bill find(ContainerRequest ctx, String id) {
        final Bill bill = super.find(ctx, id);
        if (bill == null || (accountPlan != null && !bill.getAccountPlan().equals(accountPlan.getUuid()))) return null;

        final Map<String, String> params = queryParams(ctx.getRequestUri().getQuery());
        if (Boolean.parseBoolean(params.get(PARAM_PAYMENTS))) {
            final List<AccountPayment> payments = paymentDAO.findByAccountAndAccountPlanAndBill(bill.getAccount(), bill.getAccountPlan(), bill.getUuid());
            for (AccountPayment payment : payments) {
                final String paymentMethodUuid = payment.getPaymentMethod();
                payment.setPaymentMethodObject(findPaymentMethod(paymentMethodUuid));
            }
            return bill.setPayments(payments);
        }

        return bill;
    }

    private final Map<String, AccountPaymentMethod> paymentMethodCache = new ExpirationMap<>(ExpirationEvictionPolicy.atime);
    private AccountPaymentMethod findPaymentMethod(String paymentMethodUuid) {
        return paymentMethodCache.computeIfAbsent(paymentMethodUuid, k -> paymentMethodDAO.findByUuid(k));
    }

    @Override protected List<Bill> list(ContainerRequest ctx) {
        if (accountPlan == null) return super.list(ctx);
        final long now = now();
        // don't show bills for future service
        return getDao().findByAccountAndAccountPlan(getAccountUuid(ctx), accountPlan.getUuid()).stream()
                .filter(b -> findPlan(b.getPlan()).getPeriod().periodMillis(b.getPeriodStart()) < now)
                .collect(Collectors.toList());
    }

    @Override protected Bill populate(ContainerRequest ctx, Bill bill) {
        return super.populate(ctx, bill.setPlanObject(findPlan(bill.getPlan())));
    }

    private final Map<String, BubblePlan> planCache = new ExpirationMap<>(ExpirationEvictionPolicy.atime);
    private BubblePlan findPlan(String planUuid) { return planCache.computeIfAbsent(planUuid, k -> planDAO.findByUuid(k)); }

    @Path("/{id}"+EP_PAYMENTS)
    public AccountPaymentsResource getPayments(@Context ContainerRequest ctx,
                                               @PathParam("id") String id) {
        configuration.requiresPaymentsEnabled();
        final Bill bill = super.find(ctx, id);
        if (bill == null) throw notFoundEx(id);
        return configuration.subResource(AccountPaymentsResource.class, account, bill);
    }

    @POST @Path("/{id}"+EP_PAY)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_PAYMENT,
            summary="Pay a bill",
            description="Pay a bill",
            parameters=@Parameter(name="id", description="uuid of the Bill to pay", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="true"),
                    @ApiResponse(responseCode=SC_INVALID, description="validation error, for example if the Bill has already been paid")
            }
    )
    public Response payBill(@Context ContainerRequest ctx,
                            @PathParam("id") String id,
                            AccountPaymentMethod paymentMethod) {
        configuration.requiresPaymentsEnabled();
        final Bill bill = super.find(ctx, id);
        if (bill == null) return notFound(id);
        if (bill.paid()) return invalid("err.bill.alreadyPaid");

        final AccountPaymentMethod payMethodToUse;
        if (paymentMethod == null) {
            final AccountPlan accountPlan = accountPlanDAO.findByUuid(bill.getAccountPlan());
            payMethodToUse = paymentMethodDAO.findByUuid(accountPlan.getPaymentMethod());

        } else if (paymentMethod.hasUuid()) {
            payMethodToUse = paymentMethodDAO.findByUuid(paymentMethod.getUuid());
            if (payMethodToUse == null) return invalid("err.paymentMethod.notFound");

        } else {
            final ValidationResult result = new ValidationResult();
            paymentMethod.setAccount(getAccountUuid(ctx)).validate(result, configuration);
            if (result.isInvalid()) return invalid(result);
            payMethodToUse = paymentMethodDAO.create(paymentMethod);
        }
        final CloudService paymentCloud = cloudDAO.findByUuid(payMethodToUse.getCloud());
        if (paymentCloud == null) return invalid("err.paymentService.notFound");

        final PaymentServiceDriver paymentDriver = paymentCloud.getPaymentDriver(configuration);
        if (paymentDriver.purchase(bill.getAccountPlan(), payMethodToUse.getUuid(), bill.getUuid())) {
            // re-lookup bill, should now be paid
            return ok(getDao().findByUuid(bill.getUuid()));
        } else {
            return invalid("err.purchase.declined");
        }
    }

}
