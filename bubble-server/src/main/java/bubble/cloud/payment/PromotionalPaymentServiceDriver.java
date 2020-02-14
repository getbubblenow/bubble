package bubble.cloud.payment;

import bubble.model.account.Account;
import bubble.model.bill.Promotion;

public interface PromotionalPaymentServiceDriver extends PaymentServiceDriver {

    boolean applyPromo(Promotion promo, Account caller);

    boolean applyReferralPromo(Promotion referralPromo, Account caller, Account referredFrom);

}
