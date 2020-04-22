/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.payment.free;

import bubble.cloud.payment.ChargeResult;
import bubble.cloud.payment.DefaultPaymentDriverConfig;
import bubble.cloud.payment.PaymentDriverBase;
import bubble.model.bill.*;
import bubble.notify.payment.PaymentValidationResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FreePaymentDriver extends PaymentDriverBase<DefaultPaymentDriverConfig> {

    public static final String FREE_MASK = "X".repeat(8);
    public static final String INFO_FREE = "free";

    @Override public PaymentMethodType getPaymentMethodType() { return PaymentMethodType.free; }

    @Override public PaymentValidationResult validate(AccountPaymentMethod paymentMethod) {
        if (paymentMethod.getPaymentMethodType() != PaymentMethodType.free) {
            return new PaymentValidationResult("err.paymentMethodType.mismatch");
        }
        return new PaymentValidationResult(paymentMethod.setMaskedPaymentInfo(FREE_MASK));
    }

    @Override protected ChargeResult charge(BubblePlan plan,
                                            AccountPlan accountPlan,
                                            AccountPaymentMethod paymentMethod,
                                            Bill bill,
                                            long chargeAmount) {
        return new ChargeResult().setAmountCharged(chargeAmount).setChargeId(INFO_FREE);
    }

    @Override protected String refund(AccountPlan accountPlan,
                                      AccountPayment payment,
                                      AccountPaymentMethod paymentMethod,
                                      Bill bill,
                                      long refundAmount) {
        return INFO_FREE;
    }
}
