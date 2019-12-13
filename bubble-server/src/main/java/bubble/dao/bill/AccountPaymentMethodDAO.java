package bubble.dao.bill;

import bubble.cloud.payment.PaymentValidationResult;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.AccountPlanPaymentMethod;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository @Slf4j
public class AccountPaymentMethodDAO extends AccountOwnedEntityDAO<AccountPaymentMethod> {

    @Autowired private AccountPlanDAO accountPlanDAO;
    @Autowired private AccountPlanPaymentMethodDAO planPaymentMethodDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    @Override public Object preCreate(AccountPaymentMethod paymentMethod) {
        if (paymentMethod.getPaymentMethodType().requiresClaim()) {
            final CloudService paymentService = cloudDAO.findByUuid(paymentMethod.getCloud());
            if (paymentService == null) throw invalidEx("err.paymentService.notFound");
            final PaymentValidationResult result = paymentService.getPaymentDriver(configuration).claim(paymentMethod);
            if (result.hasErrors()) throw invalidEx(result.violationsList());
        }
        return super.preCreate(paymentMethod);
    }

    @Override public void delete(String uuid) {
        final AccountPaymentMethod pm = findByUuid(uuid);
        if (pm == null) return;

        // ensure payment method is not in use by any account plan
        final List<AccountPlan> plans = accountPlanDAO.findByAccount(pm.getAccount());
        for (AccountPlan plan : plans) {
            if (plan.deleted()) continue;
            final AccountPlanPaymentMethod planPaymentMethod = planPaymentMethodDAO.findCurrentMethodForPlan(plan.getUuid());
            if (planPaymentMethod == null) {
                log.warn("delete: no AccountPlanPaymentMethod found for plan: "+plan.getUuid());
                continue;
            }
            if (planPaymentMethod.getPaymentMethod().equals(pm.getUuid())) {
                throw invalidEx("err.paymentMethod.cannotDeleteInUse");
            }
        }

        update(pm.setDeleted(true).setPaymentInfo("deleted_"+now()));
    }

    @Override public void forceDelete(String uuid) { super.delete(uuid); }

}
