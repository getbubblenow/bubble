package bubble.model.device;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum DeviceSecurityLevel {

//    disabled,  // todo: when we can identify client IP in dnscrypt-proxy, this setting will disabled DNS domain blocking
    basic,
    standard,
    maximum;

    @JsonCreator public static DeviceSecurityLevel fromString (String v) { return enumFromString(DeviceSecurityLevel.class, v); }

}
