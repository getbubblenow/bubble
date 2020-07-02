/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.device;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum DeviceSecurityLevel {

    maximum,
    standard,
    basic,
    disabled;

    @JsonCreator public static DeviceSecurityLevel fromString (String v) { return enumFromString(DeviceSecurityLevel.class, v); }

}
