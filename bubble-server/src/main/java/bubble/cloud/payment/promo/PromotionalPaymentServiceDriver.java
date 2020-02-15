package bubble.cloud.payment.promo;

import bubble.cloud.payment.PaymentServiceDriver;
import bubble.model.account.Account;
import bubble.model.account.ReferralCode;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import bubble.model.bill.Promotion;

import java.util.Map;
import java.util.Set;

public interface PromotionalPaymentServiceDriver extends PaymentServiceDriver {

    default boolean addPromoToAccount(Promotion promo, Account caller) { return false; }

    default boolean addReferralPromoToAccount(Promotion promo,
                                              Account caller,
                                              Account referredFrom,
                                              ReferralCode referralCode) { return false; }

    default boolean canUseNow(Bill bill, Promotion promo,
                              PromotionalPaymentServiceDriver promoDriver,
                              Map<Promotion, AccountPaymentMethod> promos,
                              Set<Promotion> usable,
                              AccountPlan accountPlan,
                              AccountPaymentMethod paymentMethod) {
        // do not use if deleted (should never happen)
        // do not use if other higher priority promotions are usable
        return !paymentMethod.deleted() && usable.isEmpty();
    }

}
