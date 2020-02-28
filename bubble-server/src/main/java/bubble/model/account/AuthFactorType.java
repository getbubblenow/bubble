/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.account;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum AuthFactorType {

    not_required, required, sufficient;

    @JsonCreator public static AuthFactorType fromString (String v) { return enumFromString(AuthFactorType.class, v); }

}
