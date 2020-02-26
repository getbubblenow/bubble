/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.bill;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum BillStatus {

    unpaid, partial_payment, paid;

    @JsonCreator public static BillStatus fromString (String v) { return enumFromString(BillStatus.class, v); }

}
