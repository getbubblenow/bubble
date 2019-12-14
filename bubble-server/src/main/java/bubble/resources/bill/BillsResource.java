package bubble.resources.bill;

import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.BillDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import bubble.model.cloud.CloudService;
import bubble.resources.account.ReadOnlyAccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;

import static bubble.ApiConstants.EP_PAY;
import static bubble.ApiConstants.EP_PAYMENTS;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public class BillsResource extends ReadOnlyAccountOwnedResource<Bill, BillDAO> {

    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired private CloudServiceDAO cloudDAO;

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

    @POST @Path("/{id}"+EP_PAY)
    public Response payBill(@Context ContainerRequest ctx,
                            @PathParam("id") String id,
                            AccountPaymentMethod paymentMethod) {
        final Bill bill = super.find(ctx, id);
        if (bill == null) return notFound(id);
        if (bill.paid()) return invalid("err.bill.alreadyPaid");

        final AccountPaymentMethod payMethodToUse;
        if (paymentMethod.hasUuid()) {
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
