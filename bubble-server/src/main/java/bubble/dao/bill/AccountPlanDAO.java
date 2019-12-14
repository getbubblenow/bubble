package bubble.dao.bill;

import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.*;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.background;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository
public class AccountPlanDAO extends AccountOwnedEntityDAO<AccountPlan> {

    @Autowired private AccountPlanPaymentMethodDAO accountPlanPaymentMethodDAO;
    @Autowired private BubblePlanDAO planDAO;
    @Autowired private BillDAO billDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    public AccountPlan findByAccountAndNetwork(String accountUuid, String networkUuid) {
        return findByUniqueFields("account", accountUuid, "network", networkUuid);
    }

    @Override public Object preCreate(AccountPlan accountPlan) {
        if (!accountPlan.hasPaymentMethod()) throw invalidEx("err.paymentMethod.required");

        final CloudService paymentService = cloudDAO.findByUuid(accountPlan.getPaymentMethod().getCloud());
        if (paymentService == null) throw invalidEx("err.paymentService.notFound");

        final PaymentServiceDriver paymentDriver = paymentService.getPaymentDriver(configuration);
        if (paymentDriver.getPaymentMethodType().requiresAuth()) {
            final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
            paymentDriver.authorize(plan, accountPlan, accountPlan.getPaymentMethod());
        }

        return super.preCreate(accountPlan);
    }

    @Override public AccountPlan postCreate(AccountPlan accountPlan, Object context) {
        final String accountPlanUuid = accountPlan.getUuid();
        final String paymentMethodUuid = accountPlan.getPaymentMethod().getUuid();
        accountPlanPaymentMethodDAO.create(new AccountPlanPaymentMethod()
                .setAccount(accountPlan.getAccount())
                .setAccountPlan(accountPlanUuid)
                .setPaymentMethod(paymentMethodUuid));

        final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
        final Bill bill = billDAO.create(new Bill()
                .setAccount(accountPlan.getAccount())
                .setPlan(plan.getUuid())
                .setAccountPlan(accountPlanUuid)
                .setPrice(plan.getPrice())
                .setCurrency(plan.getCurrency())
                .setPeriod(plan.getPeriod().currentPeriod())
                .setQuantity(1L)
                .setType(BillItemType.compute)
                .setStatus(BillStatus.unpaid));
        final String billUuid = bill.getUuid();

        final CloudService paymentService = cloudDAO.findByUuid(accountPlan.getPaymentMethod().getCloud());
        if (paymentService == null) throw invalidEx("err.paymentService.notFound");

        final PaymentServiceDriver paymentDriver = paymentService.getPaymentDriver(configuration);
        background(() -> {
            sleep(SECONDS.toMillis(3), "AccountPlanDAO.postCreate: waiting to finalize purchase");
            paymentDriver.purchase(accountPlanUuid, paymentMethodUuid, billUuid);
        });
        return super.postCreate(accountPlan, context);
    }

}
