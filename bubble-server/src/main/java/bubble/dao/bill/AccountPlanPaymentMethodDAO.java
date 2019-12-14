package bubble.dao.bill;

import bubble.notify.payment.PaymentValidationResult;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.AccountPlanPaymentMethod;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository @Slf4j
public class AccountPlanPaymentMethodDAO extends AccountOwnedEntityDAO<AccountPlanPaymentMethod> {

    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    @Override public Object preCreate(AccountPlanPaymentMethod planPaymentMethod) {
        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(planPaymentMethod.getPaymentMethod());
        if (paymentMethod == null) throw invalidEx("err.paymentMethod.required");
        if (paymentMethod.getPaymentMethodType().requiresClaim()) {
            final CloudService paymentService = cloudDAO.findByUuid(paymentMethod.getCloud());
            if (paymentService == null) throw invalidEx("err.paymentService.notFound");
            final PaymentValidationResult result = paymentService.getPaymentDriver(configuration).claim(planPaymentMethod);
            if (result.hasErrors()) throw invalidEx(result.violationsList());
        }
        return super.preCreate(planPaymentMethod);
    }

    @Override public Order getDefaultSortOrder() { return Order.desc("ctime"); }

    public AccountPlanPaymentMethod findCurrentMethodForPlan(String accountPlanUuid) {
        final List<AccountPlanPaymentMethod> found = findByField("accountPlan", accountPlanUuid);
        if (found.isEmpty()) return null;
        if (found.get(0).deleted()) return null;
        return found.get(0);
    }

    @Override public void delete(String uuid) {
        final AccountPlanPaymentMethod paymentMethod = findByUuid(uuid);
        if (paymentMethod == null) return;
        final List<AccountPlan> plans = accountPlanDAO.findByAccount(paymentMethod.getAccount());
        for (AccountPlan plan : plans) {
            if (plan.deleted()) continue;
            final AccountPlanPaymentMethod planPaymentMethod = findCurrentMethodForPlan(plan.getUuid());
            if (planPaymentMethod == null) {
                log.warn("delete: no AccountPlanPaymentMethod found for plan: "+plan.getUuid());
                continue;
            }
            if (planPaymentMethod.getUuid().equals(paymentMethod.getUuid())) {
                throw invalidEx("err.paymentMethod.cannotDeleteInUse");
            }
        }
        update(paymentMethod.setDeleted(true));
    }

    @Override public void forceDelete(String uuid) { super.delete(uuid); }

}
