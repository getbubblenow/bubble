package bubble.resources.bill;

import bubble.dao.bill.BillDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import bubble.resources.account.AccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import java.util.List;

import static bubble.ApiConstants.EP_PAYMENTS;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Slf4j
public class BillsResource extends AccountOwnedResource<Bill, BillDAO> {

    private AccountPlan accountPlan;

    public BillsResource(Account account) { super(account); }

    public BillsResource(Account account, AccountPlan accountPlan) {
        super(account);
        this.accountPlan = accountPlan;
    }

    @Override protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, Bill request) { return false; }
    @Override protected boolean canUpdate(ContainerRequest ctx, Account caller, Bill found, Bill request) { return false; }
    @Override protected boolean canDelete(ContainerRequest ctx, Account caller, Bill found) { return false; }

    @Override protected Bill find(ContainerRequest ctx, String id) {
        final Bill bill = super.find(ctx, id);
        return bill == null || (accountPlan != null && !bill.getPlan().equals(accountPlan.getUuid())) ? null : bill;
    }

    @Override protected List<Bill> list(ContainerRequest ctx) {
        if (accountPlan == null) return super.list(ctx);
        return getDao().findByAccountAndPlan(getAccountUuid(ctx), accountPlan.getUuid());
    }

    @Path("/{id}"+EP_PAYMENTS)
    public AccountPaymentsResource getPayments(@Context ContainerRequest ctx,
                                               @PathParam("id") String id) {
        final Bill bill = super.find(ctx, id);
        if (bill == null) throw notFoundEx(id);
        return configuration.subResource(AccountPaymentsResource.class, account, bill);
    }

}
