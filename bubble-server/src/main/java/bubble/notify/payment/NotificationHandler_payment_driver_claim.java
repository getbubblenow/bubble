package bubble.notify.payment;

import bubble.cloud.payment.PaymentServiceDriver;
import bubble.cloud.payment.PaymentValidationResult;
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

public class NotificationHandler_payment_driver_claim extends DelegatedNotificationHandlerBase {

    @Autowired protected BubbleNodeDAO nodeDAO;
    @Autowired protected CloudServiceDAO cloudDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("sender not found: "+n.getFromNode());

        final PaymentMethodClaimNotification notification = json(n.getPayloadJson(), PaymentMethodClaimNotification.class);
        final CloudService paymentService = cloudDAO.findByAccountAndName(configuration.getThisNode().getAccount(), notification.getCloud());
        final PaymentServiceDriver paymentDriver = paymentService.getPaymentDriver(configuration);

        final PaymentValidationResult result;
        if (notification.hasPlanPaymentMethod()) {
            result = paymentDriver.claim(notification.getPlanPaymentMethod());
        } else {
            result = paymentDriver.claim(notification.getPaymentMethod());
        }

        notifySender(payment_driver_response, n.getNotificationId(), sender, result);
    }

}
