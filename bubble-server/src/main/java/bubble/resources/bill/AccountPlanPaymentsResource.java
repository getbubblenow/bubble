package bubble.resources.bill;

import bubble.dao.bill.AccountPaymentDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.AccountPlanPaymentDAO;
import bubble.dao.bill.BillDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPayment;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.AccountPlanPayment;
import bubble.model.bill.Bill;
import bubble.resources.account.ReadOnlyAccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;

import static bubble.ApiConstants.EP_BILL;
import static bubble.ApiConstants.EP_PAYMENT;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public class AccountPlanPaymentsResource extends ReadOnlyAccountOwnedResource<AccountPlanPayment, AccountPlanPaymentDAO> {

    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private AccountPaymentDAO paymentDAO;
    @Autowired private BillDAO billDAO;

    private Bill bill;
    private AccountPlan accountPlan;

    public AccountPlanPaymentsResource(Account account) { super(account); }

    public AccountPlanPaymentsResource(Account account, Bill bill) {
        this(account);
        this.bill = bill;
    }

    public AccountPlanPaymentsResource(Account account, AccountPlan accountPlan) {
        this(account);
        this.accountPlan = accountPlan;
    }

    @Override protected AccountPlanPayment find(ContainerRequest ctx, String id) {
        final AccountPlanPayment planPayment = super.find(ctx, id);
        if (bill != null && !planPayment.getBill().equals(bill.getUuid())) return null;
        if (accountPlan != null && !planPayment.getAccountPlan().equals(accountPlan.getUuid())) return null;
        return planPayment;
    }

    @Override protected List<AccountPlanPayment> list(ContainerRequest ctx) {
        if (bill == null) {
            if (accountPlan == null) {
                return super.list(ctx);
            } else {
                return getDao().findByAccountAndAccountPlan(getAccountUuid(ctx), accountPlan.getUuid());
            }
        } else if (accountPlan != null) {
            return getDao().findByAccountAndAccountPlanAndBill(getAccountUuid(ctx), accountPlan.getUuid(), bill.getUuid());

        } else {
            return getDao().findByAccountAndBill(getAccountUuid(ctx), bill.getUuid());
        }
    }

    @Override protected AccountPlanPayment populate(ContainerRequest ctx, AccountPlanPayment planPayment) {
        planPayment.setAccountPlanObject(accountPlan != null ? accountPlan : accountPlanDAO.findByUuid(planPayment.getAccountPlan()));
        planPayment.setPaymentObject(paymentDAO.findByUuid(planPayment.getPayment()));
        planPayment.setBillObject(bill != null ? null : billDAO.findByUuid(planPayment.getBill()));
        return planPayment;
    }

    @Path("/{id}"+EP_PAYMENT)
    public Response getPayment(@Context ContainerRequest ctx,
                               @PathParam("id") String id) {
        final AccountPlanPayment planPayment = super.find(ctx, id);
        if (planPayment == null) throw notFoundEx(id);
        final AccountPayment payment = paymentDAO.findByUuid(planPayment.getPayment());
        return payment == null ? notFound(id) : ok(payment);
    }

    @Path("/{id}"+EP_BILL)
    public Response getBill(@Context ContainerRequest ctx,
                            @PathParam("id") String id) {
        final AccountPlanPayment planPayment = super.find(ctx, id);
        if (planPayment == null) throw notFoundEx(id);
        final Bill bill = billDAO.findByUuid(planPayment.getBill());
        return bill == null ? notFound(id) : ok(bill);
    }

}
