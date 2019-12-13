package bubble.notify.payment;

import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.DelegatedNotificationHandlerBase;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.notify.NotificationType.payment_driver_response;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

public abstract class NotificationHandler_payment_driver extends DelegatedNotificationHandlerBase {

    @Autowired protected BubbleNodeDAO nodeDAO;
    @Autowired protected AccountPaymentMethodDAO paymentMethodDAO;
    @Autowired protected CloudServiceDAO cloudDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("sender not found: "+n.getFromNode());

        final PaymentNotification paymentNotification = json(n.getPayloadJson(), PaymentNotification.class);
        final AccountPaymentMethod paymentMethod = paymentMethodDAO.findByUuid(paymentNotification.getPaymentMethodUuid());

        final CloudService paymentService = cloudDAO.findByAccountAndName(configuration.getThisNode().getAccount(), paymentMethod.getCloud());
        final boolean success = handlePaymentRequest(paymentNotification, paymentService);

        notifySender(payment_driver_response, n.getNotificationId(), sender, success);
    }

    protected abstract boolean handlePaymentRequest(PaymentNotification paymentNotification,
                                                    CloudService paymentService);

}
