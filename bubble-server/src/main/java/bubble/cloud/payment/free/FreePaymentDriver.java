package bubble.cloud.payment.free;

import bubble.cloud.payment.DefaultPaymentDriverConfig;
import bubble.cloud.payment.PaymentDriverBase;
import bubble.notify.payment.PaymentValidationResult;
import bubble.model.bill.*;

public class FreePaymentDriver extends PaymentDriverBase<DefaultPaymentDriverConfig> {

    public static final String FREE_MASK = "X".repeat(8);

    @Override public PaymentMethodType getPaymentMethodType() { return PaymentMethodType.free; }

    @Override public PaymentValidationResult validate(AccountPaymentMethod paymentMethod) {
        if (paymentMethod.getPaymentMethodType() != PaymentMethodType.free) {
            return new PaymentValidationResult("err.paymentMethodType.mismatch");
        }
        return new PaymentValidationResult(paymentMethod.setMaskedPaymentInfo(FREE_MASK));
    }

    @Override protected void charge(BubblePlan plan,
                                    AccountPlan accountPlan,
                                    AccountPaymentMethod paymentMethod,
                                    AccountPlanPaymentMethod planPaymentMethod,
                                    Bill bill) {
        // noop for free payment driver
    }

}
