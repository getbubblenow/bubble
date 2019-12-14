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

public abstract class NotificationHandler_payment_driver extends DelegatedNotificationHandlerBase {

    @Autowired protected BubbleNodeDAO nodeDAO;
    @Autowired protected CloudServiceDAO cloudDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("sender not found: "+n.getFromNode());

        final PaymentNotification paymentNotification = json(n.getPayloadJson(), PaymentNotification.class);
        final CloudService paymentService = cloudDAO.findByAccountAndName(configuration.getThisNode().getAccount(), paymentNotification.getCloud());
        PaymentResult result;
        try {
            if (handlePaymentRequest(paymentNotification, paymentService)) {
                result = PaymentResult.SUCCESS;
            } else {
                result = PaymentResult.FAILURE;
            }
        } catch (Exception e) {
            result = PaymentResult.exception(e);
        }
        notifySender(payment_driver_response, n.getNotificationId(), sender, result);
    }

    protected abstract boolean handlePaymentRequest(PaymentNotification paymentNotification,
                                                    CloudService paymentService);

}
