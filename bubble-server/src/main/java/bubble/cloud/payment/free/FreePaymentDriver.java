package bubble.cloud.payment.free;

import bubble.cloud.payment.DefaultPaymentDriverConfig;
import bubble.cloud.payment.PaymentDriverBase;
import bubble.cloud.payment.PaymentValidationResult;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.PaymentMethodType;

public class FreePaymentDriver extends PaymentDriverBase<DefaultPaymentDriverConfig> {

    public static final String FREE_MASK = "X".repeat(8);

    @Override public PaymentMethodType getPaymentMethodType() { return PaymentMethodType.free; }

    @Override public PaymentValidationResult validate(AccountPaymentMethod paymentMethod) {
        if (paymentMethod.getPaymentMethodType() != PaymentMethodType.free) {
            return new PaymentValidationResult("err.paymentMethodType.mismatch");
        }
        return new PaymentValidationResult(paymentMethod.setMaskedPaymentInfo(FREE_MASK));
    }

    @Override public boolean purchase(String accountPlanUuid, String paymentMethodUuid, String billUuid,
                                      int purchaseAmount, String currency) {
        return true;
    }

    @Override public boolean refund(String accountPlanUuid, String paymentMethodUuid, String billUuid,
                                    int refundAmount, String currency) {
        return true;
    }

}
