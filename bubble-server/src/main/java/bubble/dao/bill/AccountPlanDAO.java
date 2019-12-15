package bubble.dao.bill;

import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.*;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNetworkState;
import bubble.model.cloud.CloudService;
import bubble.notify.payment.PaymentValidationResult;
import bubble.server.BubbleConfiguration;
import bubble.service.bill.RefundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.background;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.hibernate.criterion.Restrictions.*;

@Repository
public class AccountPlanDAO extends AccountOwnedEntityDAO<AccountPlan> {

    @Autowired private BubblePlanDAO planDAO;
    @Autowired private BillDAO billDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private RefundService refundService;
    @Autowired private BubbleConfiguration configuration;

    public AccountPlan findByAccountAndNetwork(String accountUuid, String networkUuid) {
        return findByUniqueFields("account", accountUuid, "network", networkUuid);
    }

    public List<AccountPlan> findByAccountAndNotDeleted(String account) {
        return findByFields("account", account, "deleted", null);
    }

    public List<AccountPlan> findByAccountAndPaymentMethodAndNotDeleted(String account, String paymentMethod) {
        return findByFields("account", account, "paymentMethod", paymentMethod, "deleted", null);
    }

    public List<AccountPlan> findByDeletedAndNotClosed() {
        return list(criteria().add(and(
                isNotNull("deleted"),
                eq("closed", false)
        )));
    }

    @Override public Object preCreate(AccountPlan accountPlan) {
        if (configuration.paymentsEnabled()) {
            if (!accountPlan.hasPaymentMethodObject()) throw invalidEx("err.paymentMethod.required");
            if (!accountPlan.getPaymentMethodObject().hasUuid()) throw invalidEx("err.paymentMethod.required");

            if (accountPlan.getPaymentMethod() == null) {
                accountPlan.setPaymentMethod(accountPlan.getPaymentMethodObject().getUuid());
            }

            final CloudService paymentService = cloudDAO.findByUuid(accountPlan.getPaymentMethodObject().getCloud());
            if (paymentService == null) throw invalidEx("err.paymentService.notFound");

            final PaymentServiceDriver paymentDriver = paymentService.getPaymentDriver(configuration);
            if (paymentDriver.getPaymentMethodType().requiresClaim()) {
                final PaymentValidationResult result = paymentDriver.claim(accountPlan);
                if (result.hasErrors()) throw invalidEx(result.violationsList());
            }
            if (paymentDriver.getPaymentMethodType().requiresAuth()) {
                final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
                accountPlan.beforeCreate(); // ensure uuid exists
                paymentDriver.authorize(plan, accountPlan.getUuid(), accountPlan.getPaymentMethodObject());
            }
            accountPlan.setPaymentMethod(accountPlan.getPaymentMethodObject().getUuid());
        }
        return super.preCreate(accountPlan);
    }

    @Override public AccountPlan postCreate(AccountPlan accountPlan, Object context) {
        if (configuration.paymentsEnabled()) {
            final String accountPlanUuid = accountPlan.getUuid();
            final String paymentMethodUuid = accountPlan.getPaymentMethodObject().getUuid();
            final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
            final Bill bill = billDAO.create(new Bill()
                    .setAccount(accountPlan.getAccount())
                    .setPlan(plan.getUuid())
                    .setAccountPlan(accountPlanUuid)
                    .setPrice(plan.getPrice())
                    .setCurrency(plan.getCurrency())
                    .setPeriod(plan.getPeriod().currentPeriod())
                    .setPeriodStart(plan.getPeriod().getFirstPeriodStart())
                    .setPeriodEnd(plan.getPeriod().getFirstPeriodEnd())
                    .setQuantity(1L)
                    .setType(BillItemType.compute)
                    .setStatus(BillStatus.unpaid));
            final String billUuid = bill.getUuid();

            final CloudService paymentService = cloudDAO.findByUuid(accountPlan.getPaymentMethodObject().getCloud());
            if (paymentService == null) throw invalidEx("err.paymentService.notFound");

            final PaymentServiceDriver paymentDriver = paymentService.getPaymentDriver(configuration);
            background(() -> {
                sleep(SECONDS.toMillis(3), "AccountPlanDAO.postCreate: waiting to finalize purchase");
                paymentDriver.purchase(accountPlanUuid, paymentMethodUuid, billUuid);
            });
        }
        return super.postCreate(accountPlan, context);
    }

    @Override public void delete(String uuid) {
        final AccountPlan accountPlan = findByUuid(uuid);
        if (accountPlan == null) return;

        final BubbleNetwork network = networkDAO.findByUuid(accountPlan.getNetwork());
        if (network != null && network.getState() != BubbleNetworkState.stopped) {
            throw invalidEx("err.accountPlan.stopNetworkBeforeDeleting");
        }
        update(accountPlan.setDeleted(now()).setEnabled(false));
        if (configuration.paymentsEnabled()) {
            refundService.processRefunds();
        }
    }

    @Override public void forceDelete(String uuid) { super.delete(uuid); }

}
