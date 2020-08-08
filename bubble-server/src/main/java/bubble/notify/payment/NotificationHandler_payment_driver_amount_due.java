/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.payment;

import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.DelegatedNotificationHandlerBase;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.notify.NotificationType.payment_driver_response;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

public class NotificationHandler_payment_driver_amount_due extends DelegatedNotificationHandlerBase {

    @Autowired protected BubbleNodeDAO nodeDAO;
    @Autowired protected CloudServiceDAO cloudDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("sender not found: "+n.getFromNode());

        final PaymentNotification paymentNotification = json(n.getPayloadJson(), PaymentNotification.class);
        final CloudService paymentService = cloudDAO.findByAccountAndName(configuration.getThisNode().getAccount(), paymentNotification.getCloud());
        final long amountDue = paymentService.getPaymentDriver(configuration).amountDue(
                paymentNotification.getAccountPlanUuid(),
                paymentNotification.getBillUuid(),
                paymentNotification.getPaymentMethodUuid());
        notifySender(payment_driver_response, n.getNotificationId(), sender, amountDue);
    }

}
