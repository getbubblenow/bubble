/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.device;

import bubble.model.CertType;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static bubble.ApiConstants.enumFromString;

@AllArgsConstructor
public enum BubbleDeviceType {

    uninitialized (null, false),
    windows       (CertType.cer, true),
    macosx        (CertType.pem, true),
    ios           (CertType.pem, true),
    android       (CertType.cer, true),
    linux         (CertType.crt, true),
    firefox       (CertType.crt, false),
    other         (null, true);

    @Getter private CertType certType;
    @Getter private boolean selectable;

    @JsonCreator public static BubbleDeviceType fromString (String v) { return enumFromString(BubbleDeviceType.class, v); }

    @Getter(lazy=true) private static final List<BubbleDeviceType> selectableTypes = initSelectable();
    private static List<BubbleDeviceType> initSelectable() {
        return Arrays.stream(values())
                .filter(BubbleDeviceType::isSelectable)
                .collect(Collectors.toList());
    }

}
