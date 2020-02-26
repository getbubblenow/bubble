/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.bill;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AccountPaymentStatus {

    init, success, failure, unknown;

    @JsonCreator public static AccountPaymentStatus fromString (String v) { return enumFromString(AccountPaymentStatus.class, v); }

}
