/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
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
    windows       (CertType.cer, true, DeviceSecurityLevel.standard),
    macosx        (CertType.pem, true, DeviceSecurityLevel.standard),
    ios           (CertType.pem, true, DeviceSecurityLevel.standard),
    android       (CertType.cer, true, DeviceSecurityLevel.basic),
    linux         (CertType.crt, true, DeviceSecurityLevel.standard),
    firefox       (CertType.crt, false),
    web_client    (null, false, DeviceSecurityLevel.disabled),
    other         (null, true, DeviceSecurityLevel.basic);

    @Getter private final CertType certType;
    @Getter private final boolean selectable;
    @Getter private final DeviceSecurityLevel defaultSecurityLevel;
    public boolean hasDefaultSecurityLevel () { return defaultSecurityLevel != null; }

    BubbleDeviceType (CertType certType, boolean selectable) { this(certType, selectable, null); }

    @JsonCreator public static BubbleDeviceType fromString (String v) { return enumFromString(BubbleDeviceType.class, v); }

    public boolean isNonVpnDevice () { return this == web_client; }
    public boolean isVpnDevice () { return !isNonVpnDevice(); }

    @Getter(lazy=true) private static final List<BubbleDeviceType> selectableTypes = initSelectable();
    private static List<BubbleDeviceType> initSelectable() {
        return Arrays.stream(values())
                .filter(BubbleDeviceType::isSelectable)
                .collect(Collectors.toList());
    }

}
