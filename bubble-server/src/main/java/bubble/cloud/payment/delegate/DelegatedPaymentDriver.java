package bubble.cloud.payment.delegate;

import bubble.cloud.CloudServiceType;
import bubble.cloud.DelegatedCloudServiceDriverBase;
import bubble.cloud.payment.PaymentServiceDriver;
import bubble.cloud.payment.PaymentValidationResult;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlanPaymentMethod;
import bubble.model.bill.PaymentMethodType;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.notify.payment.PaymentMethodClaimNotification;
import bubble.notify.payment.PaymentMethodValidationNotification;
import bubble.notify.payment.PaymentNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.notify.NotificationType.*;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public class DelegatedPaymentDriver extends DelegatedCloudServiceDriverBase implements PaymentServiceDriver {

    @Autowired private CloudServiceDAO cloudDAO;

    public DelegatedPaymentDriver(CloudService cloud) { super(cloud); }

    @Override public CloudServiceType getType() { return CloudServiceType.payment; }

    @Override public PaymentMethodType getPaymentMethodType() {
        if (!cloud.delegated()) {
            log.warn("getPaymentMethodType: delegated driver has non-delegated cloud: "+cloud.getUuid());
            return cloud.getPaymentDriver(configuration).getPaymentMethodType();
        }
        final CloudService delegate = cloudDAO.findByUuid(cloud.getDelegated());
        if (delegate == null) throw invalidEx("err.paymentService.notFound");
        return delegate.getPaymentDriver(configuration).getPaymentMethodType();
    }

    @Override public PaymentValidationResult validate(AccountPaymentMethod paymentMethod) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, payment_driver_validate,
                new PaymentMethodValidationNotification(paymentMethod, cloud.getName()));
    }

    @Override public PaymentValidationResult claim(AccountPaymentMethod paymentMethod) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, payment_driver_claim,
                new PaymentMethodClaimNotification(paymentMethod, cloud.getName()));
    }

    @Override public PaymentValidationResult claim(AccountPlanPaymentMethod planPaymentMethod) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, payment_driver_claim,
                new PaymentMethodClaimNotification(planPaymentMethod, cloud.getName()));
    }

    @Override public boolean purchase(String accountPlanUuid,
                                      String paymentMethodUuid,
                                      String billUuid,
                                      int purchaseAmount,
                                      String currency) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, payment_driver_purchase,
                new PaymentNotification(accountPlanUuid, paymentMethodUuid, billUuid, purchaseAmount, currency));
    }

    @Override public boolean refund(String accountPlanUuid,
                                    String paymentMethodUuid,
                                    String billUuid,
                                    int refundAmount,
                                    String currency) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, payment_driver_refund,
                new PaymentNotification(accountPlanUuid, paymentMethodUuid, billUuid, refundAmount, currency));
    }

}
