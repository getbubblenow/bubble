/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.bill;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AccountPaymentType {

    payment, credit_applied, refund;

    @JsonCreator public static AccountPaymentType fromString (String v) { return enumFromString(AccountPaymentType.class, v); }

}
