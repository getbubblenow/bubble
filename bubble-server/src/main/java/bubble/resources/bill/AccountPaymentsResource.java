package bubble.resources.bill;

import bubble.dao.bill.AccountPaymentDAO;
import bubble.dao.bill.AccountPlanPaymentDAO;
import bubble.dao.bill.BillDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPayment;
import bubble.model.bill.AccountPlanPayment;
import bubble.model.bill.Bill;
import bubble.resources.account.ReadOnlyAccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static bubble.ApiConstants.EP_BILLS;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFound;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class AccountPaymentsResource extends ReadOnlyAccountOwnedResource<AccountPayment, AccountPaymentDAO> {

    @Autowired private AccountPlanPaymentDAO planPaymentDAO;
    @Autowired private BillDAO billDAO;

    public AccountPaymentsResource(Account account) { super(account); }

    @GET @Path("/{id}"+EP_BILLS)
    public Response getBills(@Context ContainerRequest ctx,
                             @PathParam("id") String id) {
        final AccountPayment accountPayment = super.find(ctx, id);
        if (accountPayment == null) return notFound(id);

        final List<AccountPlanPayment> planPayments = planPaymentDAO.findByAccountPayment(accountPayment.getUuid());
        final List<Bill> bills = new ArrayList<>();
        for (AccountPlanPayment app : planPayments) {
            final Bill bill = billDAO.findByUuid(app.getBill());
            if (bill == null) {
                log.warn("getBills: bill "+app.getBill()+" not found for AccountPlanPayment="+app.getUuid());
                continue;
            }
            bills.add(bill);
        }
        return ok(bills);
    }

}
