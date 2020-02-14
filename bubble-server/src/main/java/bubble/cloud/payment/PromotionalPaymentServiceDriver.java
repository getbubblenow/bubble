package bubble.cloud.payment;

import bubble.model.account.Account;
import bubble.model.bill.Promotion;

public interface PromotionalPaymentServiceDriver extends PaymentServiceDriver {

    void applyPromo(Promotion promo, Account caller);

    void applyReferralPromo(Promotion referralPromo, Account caller, Account referredFrom);

}
