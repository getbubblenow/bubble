package bubble.cloud.payment;

import bubble.cloud.CloudServiceType;
import bubble.model.bill.*;
import bubble.notify.payment.PaymentValidationResult;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

public interface PaymentServiceDriver {

    default CloudServiceType getType() { return CloudServiceType.payment; }

    PaymentMethodType getPaymentMethodType();

    PaymentValidationResult validate(AccountPaymentMethod paymentMethod);

    default PaymentValidationResult claim(AccountPaymentMethod paymentMethod) { return notSupported("claim"); }
    default PaymentValidationResult claim(AccountPlanPaymentMethod planPaymentMethod) { return notSupported("claim"); }

    boolean authorize(BubblePlan plan, AccountPlan accountPlan, AccountPaymentMethod paymentMethod);

    boolean purchase(String accountPlanUuid, String paymentMethodUuid, String billUuid);

    boolean refund(String accountPlanUuid, String paymentMethodUuid, String billUuid, long refundAmount);
}
