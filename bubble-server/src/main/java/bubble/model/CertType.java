/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum CertType {

    pem, p12, cer, crt;

    @JsonCreator public static CertType fromString (String v) { return enumFromString(CertType.class, v); }

}
