/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account.message;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum ActionTarget {

    network, account;

    @JsonCreator public static ActionTarget fromString (String v) { return enumFromString(ActionTarget.class, v); }

}
