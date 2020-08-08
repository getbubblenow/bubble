/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.payment.promo;

import bubble.cloud.payment.ChargeResult;
import bubble.cloud.payment.PaymentDriverBase;
import bubble.dao.bill.AccountPaymentMethodDAO;
import bubble.dao.bill.PromotionDAO;
import bubble.model.bill.*;
import bubble.notify.payment.PaymentValidationResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.cloud.payment.ChargeResult.ZERO_CHARGE;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Slf4j
public abstract class PromotionalPaymentDriverBase<T> extends PaymentDriverBase<T> implements PromotionalPaymentServiceDriver {

    @Getter @Autowired private AccountPaymentMethodDAO accountPaymentMethodDAO;
    @Getter @Autowired private PromotionDAO promotionDAO;

    @Override public PaymentMethodType getPaymentMethodType() { return PaymentMethodType.promotional_credit; }

    @Override public PaymentValidationResult validate(AccountPaymentMethod paymentMethod) {
        if (paymentMethod.getPaymentMethodType() != PaymentMethodType.promotional_credit || !paymentMethod.hasPromotion()) {
            return new PaymentValidationResult("err.paymentMethodType.mismatch");
        }
        if (!paymentMethod.getCloud().equals(cloud.getUuid()) || paymentMethod.deleted()) {
            return new PaymentValidationResult("err.paymentMethodType.mismatch");
        }
        if (!paymentMethod.hasPromotion()) {
            return new PaymentValidationResult("err.paymentMethodType.mismatch");
        }
        final Promotion promotion = promotionDAO.findByUuid(paymentMethod.getPromotion());
        if (promotion == null || promotion.disabled()) {
            return new PaymentValidationResult("err.paymentMethodType.mismatch");
        }
        return new PaymentValidationResult(paymentMethod);
    }

    @Override protected ChargeResult charge(BubblePlan plan,
                                            AccountPlan accountPlan,
                                            AccountPaymentMethod paymentMethod,
                                            Bill bill,
                                            long chargeAmount) {
        // sanity checks
        if (!paymentMethod.hasPromotion()) {
            reportError("charge: paymentMethod "+paymentMethod.getUuid()+" has no promotion, returning zero charge");
            return ZERO_CHARGE;
        }
        final Promotion promotion = getPromotionDAO().findByUuid(paymentMethod.getPromotion());
        if (promotion == null) {
            reportError("charge: paymentMethod "+paymentMethod.getUuid()+": promotion not found: "+paymentMethod.getPromotion()+", returning zero charge");
            return ZERO_CHARGE;
        }

        // mark deleted so it will not be found/applied for future transactions
        log.info("charge: applying promotion: "+paymentMethod.getPromotion()+" via AccountPaymentMethod: "+paymentMethod.getUuid());
        paymentMethodDAO.update(paymentMethod.setDeleted());

        final long promoValue = getPromoValue(chargeAmount, promotion);
        return promoValue == 0 ? ZERO_CHARGE : new ChargeResult().setAmountCharged(promoValue).setChargeId(getClass().getSimpleName());
    }

    @Override protected String refund(AccountPlan accountPlan,
                                      AccountPayment payment,
                                      AccountPaymentMethod paymentMethod,
                                      Bill bill,
                                      long refundAmount) {
        reportError(getClass().getSimpleName()+": refund: cannot issue, ignoring");
        return getClass().getName();
    }

}
