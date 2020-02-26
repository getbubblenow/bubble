/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify.payment;

import bubble.model.cloud.CloudService;

public class NotificationHandler_payment_driver_refund extends NotificationHandler_payment_driver {

    @Override public boolean handlePaymentRequest(PaymentNotification paymentNotification, CloudService paymentService) {
        return paymentService.getPaymentDriver(configuration).refund(paymentNotification.getAccountPlanUuid());
    }

}
