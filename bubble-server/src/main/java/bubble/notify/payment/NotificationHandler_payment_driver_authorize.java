package bubble.notify.payment;

import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.AccountPlanDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.CloudService;
import org.springframework.beans.factory.annotation.Autowired;

public class NotificationHandler_payment_driver_authorize extends NotificationHandler_payment_driver {

    @Autowired private BubblePlanDAO planDAO;
    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired private AccountPlanDAO accountPlanDAO;

    @Override public boolean handlePaymentRequest(PaymentNotification paymentNotification, CloudService paymentService) {
        final BubblePlan plan = planDAO.findByUuid(paymentNotification.getPlanUuid());
        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(paymentNotification.getPaymentMethodUuid());
        return paymentService.getPaymentDriver(configuration).authorize(plan, paymentMethod);
    }

}
