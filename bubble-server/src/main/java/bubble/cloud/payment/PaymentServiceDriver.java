package bubble.cloud.payment;

import bubble.cloud.CloudServiceType;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlanPaymentMethod;
import bubble.model.bill.PaymentMethodType;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

public interface PaymentServiceDriver {

    default CloudServiceType getType() { return CloudServiceType.payment; }

    PaymentMethodType getPaymentMethodType();

    PaymentValidationResult validate(AccountPaymentMethod paymentMethod);

    default PaymentValidationResult claim(AccountPaymentMethod paymentMethod) { return notSupported("claim"); }
    default PaymentValidationResult claim(AccountPlanPaymentMethod planPaymentMethod) { return notSupported("claim"); }

    boolean purchase(String accountPlanUuid, String paymentMethodUuid, String billUuid,
                     int purchaseAmount, String currency);

    boolean refund(String accountPlanUuid, String paymentMethodUuid, String billUuid,
                   int refundAmount, String currency);

}
