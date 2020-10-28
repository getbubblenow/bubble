/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum ConnectionCheckResponse {

    /**
     * Default response. Indicates a rule has no preference, it does not care about this connection
     */
    noop,

    /**
     * Indicates that a rule has enabled TLS passthru for the connection
     */
    passthru,

    /**
     * Indicates that a rule wants to block this connection
     */
    block,

    /**
     * Indicates that a rule wants to filter this connection
     */
    filter,

    /**
     * An error occurred processing the rule logic for this connection
     */
    error;

    @JsonCreator public static ConnectionCheckResponse fromString (String v) { return enumFromString(ConnectionCheckResponse.class, v); }

}
