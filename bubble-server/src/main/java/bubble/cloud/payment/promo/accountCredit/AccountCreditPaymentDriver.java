/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.payment.promo.accountCredit;

import bubble.cloud.payment.ChargeResult;
import bubble.cloud.payment.promo.PromotionalPaymentDriverBase;
import bubble.cloud.payment.promo.PromotionalPaymentServiceDriver;
import bubble.model.account.Account;
import bubble.model.bill.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@Slf4j
public class AccountCreditPaymentDriver extends PromotionalPaymentDriverBase<AccountCreditPaymentConfig> {

    @Override public boolean adminAddPromoToAccount(Promotion promo, Account account) {
        paymentMethodDAO.create(new AccountPaymentMethod()
                .setAccount(account.getUuid())
                .setCloud(promo.getCloud())
                .setPaymentMethodType(PaymentMethodType.promotional_credit)
                .setPaymentInfo(promo.getName()+"_"+now())
                .setMaskedPaymentInfo(promo.getName())
                .setPromotion(promo.getUuid()));
        return true;
    }

    @Override public boolean canUseNow(Bill bill,
                                       Promotion promo,
                                       PromotionalPaymentServiceDriver promoDriver,
                                       List<Promotion> promos,
                                       Set<Promotion> usable,
                                       AccountPlan accountPlan,
                                       AccountPaymentMethod paymentMethod) {
        return promo.getPaymentMethod().notDeleted();
    }

    @Override protected ChargeResult getChargeResult(long chargeAmount, Promotion promotion) {
        if (config.fullBill()) {
            return new ChargeResult().setAmountCharged(chargeAmount).setChargeId(getClass().getName());
        }
        final int amount = chargeAmount > config.getCreditAmount() ? config.getCreditAmount() : (int) chargeAmount;
        return super.getChargeResult(amount, promotion);
    }

}
