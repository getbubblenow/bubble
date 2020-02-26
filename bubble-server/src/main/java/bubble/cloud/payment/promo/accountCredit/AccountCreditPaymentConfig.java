/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.payment.promo.accountCredit;

import bubble.cloud.payment.promo.PromotionPaymentConfig;
import lombok.Getter;
import lombok.Setter;

public class AccountCreditPaymentConfig extends PromotionPaymentConfig {

    @Getter @Setter private Integer creditAmount;
    @Getter @Setter private Boolean fullBill;
    public boolean fullBill () { return fullBill != null && fullBill; }

}
