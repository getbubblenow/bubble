package bubble.resources.bill;

import bubble.dao.bill.BillDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import bubble.resources.account.ReadOnlyAccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import java.util.List;

import static bubble.ApiConstants.EP_PAYMENTS;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Slf4j
public class BillsResource extends ReadOnlyAccountOwnedResource<Bill, BillDAO> {

    private AccountPlan accountPlan;

    public BillsResource(Account account) { super(account); }

    public BillsResource(Account account, AccountPlan accountPlan) {
        super(account);
        this.accountPlan = accountPlan;
    }

    @Override protected Bill find(ContainerRequest ctx, String id) {
        final Bill bill = super.find(ctx, id);
        return bill == null || (accountPlan != null && !bill.getAccountPlan().equals(accountPlan.getUuid())) ? null : bill;
    }

    @Override protected List<Bill> list(ContainerRequest ctx) {
        if (accountPlan == null) return super.list(ctx);
        return getDao().findByAccountAndAccountPlan(getAccountUuid(ctx), accountPlan.getUuid());
    }

    @Path("/{id}"+EP_PAYMENTS)
    public AccountPaymentsResource getPayments(@Context ContainerRequest ctx,
                                               @PathParam("id") String id) {
        final Bill bill = super.find(ctx, id);
        if (bill == null) throw notFoundEx(id);
        return configuration.subResource(AccountPaymentsResource.class, account, bill);
    }

}
