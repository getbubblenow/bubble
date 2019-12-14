package bubble.notify.payment;

import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.CloudService;
import org.springframework.beans.factory.annotation.Autowired;

public class NotificationHandler_payment_driver_authorize extends NotificationHandler_payment_driver {

    @Autowired private BubblePlanDAO planDAO;
    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired private AccountPlanDAO accountPlanDAO;

    @Override public boolean handlePaymentRequest(PaymentNotification paymentNotification, CloudService paymentService) {
        // todo: this returns null because the AccountPlan has not yet been created....
        final AccountPlan accountPlan = accountPlanDAO.findByUuid(paymentNotification.getAccountPlanUuid());
        final BubblePlan plan = planDAO.findByUuid(accountPlan.getPlan());
        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(paymentNotification.getPaymentMethodUuid());
        return paymentService.getPaymentDriver(configuration).authorize(plan, accountPlan, paymentMethod);
    }

}
