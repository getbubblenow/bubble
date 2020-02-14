package bubble.cloud.payment.firstMonthFree;

import bubble.cloud.payment.PaymentDriverBase;
import bubble.cloud.payment.PromotionalPaymentServiceDriver;
import bubble.model.account.Account;
import bubble.model.bill.*;
import bubble.notify.payment.PaymentValidationResult;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

public class FirstMonthFreePaymentDriver extends PaymentDriverBase<FirstMonthPaymentConfig> implements PromotionalPaymentServiceDriver {

    @Override public PaymentMethodType getPaymentMethodType() { return PaymentMethodType.promotional_credit; }

    @Override public void applyPromo(Promotion promo, Account caller) {
        // todo
    }

    @Override public void applyReferralPromo(Promotion referralPromo, Account caller, Account referredFrom) { notSupported("applyReferralPromo"); }

    @Override public PaymentValidationResult validate(AccountPaymentMethod paymentMethod) {
        return null;
    }

    @Override protected String charge(BubblePlan plan, AccountPlan accountPlan, AccountPaymentMethod paymentMethod, Bill bill) {
        return null;
    }

    @Override protected String refund(AccountPlan accountPlan, AccountPayment payment, AccountPaymentMethod paymentMethod, Bill bill, long refundAmount) {
        return null;
    }

}
