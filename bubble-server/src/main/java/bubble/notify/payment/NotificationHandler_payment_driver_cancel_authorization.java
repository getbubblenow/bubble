/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.payment;

import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.CloudService;
import org.springframework.beans.factory.annotation.Autowired;

public class NotificationHandler_payment_driver_cancel_authorization extends NotificationHandler_payment_driver {

    @Autowired private BubblePlanDAO planDAO;
    @Autowired private AccountPaymentMethodDAO paymentMethodDAO;

    @Override public boolean handlePaymentRequest(PaymentNotification paymentNotification, CloudService paymentService) {
        final BubblePlan plan = planDAO.findByUuid(paymentNotification.getPlanUuid());
        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(paymentNotification.getPaymentMethodUuid());
        final PaymentServiceDriver paymentDriver = paymentService.getPaymentDriver(configuration);
        return paymentDriver.cancelAuthorization(plan, paymentNotification.getAccountPlanUuid(), paymentMethod);
    }

}
