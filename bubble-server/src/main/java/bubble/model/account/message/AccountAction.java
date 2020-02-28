/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.account.message;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AccountAction {

    login, password, verify, download, start, stop, delete, welcome, info, promo, payment;

    @JsonCreator public static AccountAction fromString (String v) { return enumFromString(AccountAction.class, v); }

}
