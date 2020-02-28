/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class ChargeResult {

    public static final ChargeResult ZERO_CHARGE = new ChargeResult().setAmountCharged(0);

    @Getter @Setter private String chargeId;
    @Getter @Setter private long amountCharged;

}
