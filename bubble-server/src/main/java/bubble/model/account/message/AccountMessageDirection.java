/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.account.message;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AccountMessageDirection {

    to_account, from_account;

    @JsonCreator public static AccountMessageDirection fromString (String v) { return enumFromString(AccountMessageDirection.class, v); }

}
