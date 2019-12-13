package bubble.resources.bill;

import bubble.dao.bill.AccountPaymentDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPayment;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import bubble.resources.account.AccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import java.util.List;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class AccountPaymentsResource extends AccountOwnedResource<AccountPayment, AccountPaymentDAO> {

    private AccountPlan accountPlan;
    private Bill bill;

    public AccountPaymentsResource(Account account) { super(account); }

    public AccountPaymentsResource(Account account, AccountPlan accountPlan) {
        super(account);
        this.accountPlan = accountPlan;
    }

    public AccountPaymentsResource(Account account, Bill bill) {
        super(account);
        this.bill = bill;
    }

    @Override protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, AccountPayment request) { return false; }
    @Override protected boolean canUpdate(ContainerRequest ctx, Account caller, AccountPayment found, AccountPayment request) { return false; }
    @Override protected boolean canDelete(ContainerRequest ctx, Account caller, AccountPayment found) { return false; }

    @Override protected AccountPayment find(ContainerRequest ctx, String id) {
        final AccountPayment payment = super.find(ctx, id);
        return payment == null
                || (accountPlan != null && !payment.getPlan().equals(accountPlan.getUuid()))
                || (bill != null && !payment.getBill().equals(bill.getUuid()))
                ? null : payment;
    }

    @Override protected List<AccountPayment> list(ContainerRequest ctx) {
        if (accountPlan == null && bill == null) return super.list(ctx);
        if (bill != null) return getDao().findByAccountAndBill(getAccountUuid(ctx), bill.getUuid());
        return getDao().findByAccountAndPlan(getAccountUuid(ctx), accountPlan.getUuid());
    }

}
