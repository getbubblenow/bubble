/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum ConnectionCheckResponse {

    noop, passthru, block, error;

    @JsonCreator public static ConnectionCheckResponse fromString (String v) { return enumFromString(ConnectionCheckResponse.class, v); }

}
