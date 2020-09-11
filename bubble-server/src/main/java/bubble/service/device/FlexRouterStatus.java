/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.device;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum FlexRouterStatus {

    none, active, unreachable, deleted;

    @JsonCreator public static FlexRouterStatus fromString (String v) { return enumFromString(FlexRouterStatus.class, v); }

}
