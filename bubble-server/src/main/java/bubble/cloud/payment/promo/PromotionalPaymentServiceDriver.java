/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.payment.promo;

import bubble.cloud.payment.PaymentServiceDriver;
import bubble.model.account.Account;
import bubble.model.account.ReferralCode;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.AccountPlan;
import bubble.model.bill.Bill;
import bubble.model.bill.Promotion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public interface PromotionalPaymentServiceDriver extends PaymentServiceDriver {

    Logger log = LoggerFactory.getLogger(PromotionalPaymentServiceDriver.class);

    default boolean adminAddPromoToAccount(Promotion promo, Account account) { return false; }

    default boolean addPromoToAccount(Promotion promo, Account caller) { return false; }

    default boolean addReferralPromoToAccount(Promotion promo,
                                              Account caller,
                                              Account referredFrom,
                                              ReferralCode referralCode) { return false; }

    default boolean canUseNow(Bill bill,
                              Promotion promo,
                              PromotionalPaymentServiceDriver promoDriver,
                              List<Promotion> promos,
                              Set<Promotion> usable,
                              AccountPlan accountPlan,
                              AccountPaymentMethod paymentMethod) {
        // do not use if deleted (should never happen)
        // do not use if wrong currency (should never happen)
        // do not use if other higher priority promotions are usable
        return paymentMethod.notDeleted() && promo.isCurrency(bill.getCurrency()) && usable.isEmpty();
    }

    default long getPromoValue(long chargeAmount, Promotion promotion) {
        // check minimum
        if (chargeAmount < promotion.getMinValue()) {
            log.warn("getPromoValue: chargeAmount ("+chargeAmount+") < promotion.minValue ("+promotion.getMinValue()+"), returning zero");
            return 0;
        }
        // apply up to maximum
        if (chargeAmount > promotion.getMaxValue()) {
            log.warn("getPromoValue: chargeAmount ("+chargeAmount+") > promotion.maxValue ("+promotion.getMinValue()+"), returning maxValue");
            return promotion.getMaxValue();
        } else {
            return chargeAmount;
        }
    }

}
