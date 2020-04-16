/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.bill;

import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.resources.account.AccountOwnedResource;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.URIUtil.queryParams;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class AccountPaymentMethodsResource extends AccountOwnedResource<AccountPaymentMethod, AccountPaymentMethodDAO> {

    public static final String PARAM_ALL = "all";

    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private BubbleConfiguration configuration;

    public AccountPaymentMethodsResource(Account account) { super(account); }

    @Override protected AccountPaymentMethod find(ContainerRequest ctx, String id) {
        final AccountPaymentMethod found = super.find(ctx, id);
        return found == null || found.deleted() ? null : found;
    }

    @Override protected AccountPaymentMethod findAlternate(ContainerRequest ctx, AccountPaymentMethod request) {
        return !request.hasPaymentInfo() ? null : getDao().findByAccountAndPaymentInfo(getAccountUuid(ctx), request.getPaymentInfo());
    }

    @Override protected List<AccountPaymentMethod> list(ContainerRequest ctx) {
        final Map<String, String> params = queryParams(ctx.getRequestUri().getQuery());
        if (params.containsKey(PARAM_ALL) && Boolean.parseBoolean(params.get("all").toLowerCase())) {
            return setPlanNames(super.list(ctx));
        } else {
            return setPlanNames(super.list(ctx).stream().filter(AccountPaymentMethod::notDeleted).collect(Collectors.toList()));
        }
    }

    private List<AccountPaymentMethod> setPlanNames(List<AccountPaymentMethod> paymentMethods) {
        for (AccountPaymentMethod apm : paymentMethods) {
            final List<AccountPlan> plans = accountPlanDAO.findByAccountAndPaymentMethodAndNotDeleted(apm.getAccount(), apm.getUuid());
            apm.setPlanNames(plans.stream().map(AccountPlan::getName).collect(Collectors.toList()));
        }
        return paymentMethods;
    }

    @Override protected AccountPaymentMethod setReferences(ContainerRequest ctx, Account caller, AccountPaymentMethod request) {
        final ValidationResult result = new ValidationResult();
        request.validate(result, configuration);
        if (result.isInvalid()) throw invalidEx(result);
        return super.setReferences(ctx, caller, request);
    }

    @Override protected boolean canUpdate(ContainerRequest ctx, Account caller, AccountPaymentMethod found, AccountPaymentMethod request) {
        return false;
    }

}
