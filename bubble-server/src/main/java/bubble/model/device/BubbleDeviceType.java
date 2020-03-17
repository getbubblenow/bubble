/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.device;

import bubble.model.CertType;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static bubble.ApiConstants.enumFromString;

@AllArgsConstructor
public enum BubbleDeviceType {

    uninitialized (null),
    windows       (CertType.cer),
    macosx        (CertType.pem),
    ios           (CertType.pem),
    android       (CertType.cer),
    linux         (CertType.crt),
    firefox       (CertType.crt),
    other         (null);

    @Getter private CertType certType;

    @JsonCreator public static BubbleDeviceType fromString (String v) { return enumFromString(BubbleDeviceType.class, v); }

}
