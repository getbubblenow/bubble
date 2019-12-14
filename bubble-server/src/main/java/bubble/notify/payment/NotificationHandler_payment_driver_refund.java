package bubble.notify.payment;

import bubble.model.cloud.CloudService;

public class NotificationHandler_payment_driver_refund extends NotificationHandler_payment_driver {

    @Override public boolean handlePaymentRequest(PaymentNotification paymentNotification, CloudService paymentService) {
        return paymentService.getPaymentDriver(configuration).refund(
                paymentNotification.getAccountPlanUuid(),
                paymentNotification.getPaymentMethodUuid(),
                paymentNotification.getBillUuid(),
                paymentNotification.getAmount()
        );
    }

}
