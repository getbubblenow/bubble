/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.payment;

import bubble.model.cloud.CloudService;

public class NotificationHandler_payment_driver_purchase extends NotificationHandler_payment_driver {

    @Override public boolean handlePaymentRequest(PaymentNotification paymentNotification, CloudService paymentService) {
        return paymentService.getPaymentDriver(configuration).purchase(
                paymentNotification.getAccountPlanUuid(),
                paymentNotification.getPaymentMethodUuid(),
                paymentNotification.getBillUuid()
        );
    }

}
