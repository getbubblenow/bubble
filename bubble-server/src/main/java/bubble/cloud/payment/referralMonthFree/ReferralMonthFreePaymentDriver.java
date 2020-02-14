package bubble.cloud.payment.referralMonthFree;

import bubble.cloud.payment.PaymentDriverBase;
import bubble.cloud.payment.PromotionalPaymentServiceDriver;
import bubble.model.account.Account;
import bubble.model.bill.*;
import bubble.notify.payment.PaymentValidationResult;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

public class ReferralMonthFreePaymentDriver extends PaymentDriverBase<ReferralMonthPaymentConfig> implements PromotionalPaymentServiceDriver {

    @Override public PaymentMethodType getPaymentMethodType() { return PaymentMethodType.promotional_credit; }

    @Override public void applyPromo(Promotion promo, Account caller) { notSupported("applyPromo"); }

    @Override public void applyReferralPromo(Promotion referralPromo, Account caller, Account referredFrom) {
        // todo
        // validate referralPromo
        // check existing AccountPaymentMethods for caller, they can only have one AccountPaymentMethod of the "joiner" type across all methods
        // -- create if not exist
        // check existing AccountPaymentMethods for referredFrom, they can only have one AccountPaymentMethod of the "referral" type for the caller
        // -- create if not exist
    }

    @Override public PaymentValidationResult validate(AccountPaymentMethod paymentMethod) {
        // todo
        // validate that this paymentMethod is for this driver
        // validate that this paymentMethod has not yet been used on any other AccountPayment
        return null;
    }

    @Override protected String charge(BubblePlan plan, AccountPlan accountPlan, AccountPaymentMethod paymentMethod, Bill bill) {
        // todo
        // validate that this paymentMethod is for this driver
        // validate that this paymentMethod has not yet been used on any other AccountPayment
        // apply the paymentMethod
        return null;
    }

    @Override protected String refund(AccountPlan accountPlan, AccountPayment payment, AccountPaymentMethod paymentMethod, Bill bill, long refundAmount) {
        // cannot refund
        throw invalidEx("err.refund.noRefundsForPromotionalCredits");
    }

}
