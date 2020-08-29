/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.device;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;

import static bubble.ApiConstants.enumFromString;

@AllArgsConstructor
public enum DeviceSecurityLevel {

    maximum  (true),
    standard (true),
    basic    (false),
    disabled (false);

    @JsonCreator public static DeviceSecurityLevel fromString (String v) { return enumFromString(DeviceSecurityLevel.class, v); }

    private final boolean supportsRequestModification;
    public boolean supportsRequestModification() { return supportsRequestModification; }

}
