/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static bubble.ApiConstants.enumFromString;
import static org.cobbzilla.util.http.HttpStatusCodes.NOT_FOUND;
import static org.cobbzilla.util.http.HttpStatusCodes.OK;

@AllArgsConstructor
public enum FilterMatchDecision {

    no_match (OK),         // associated matcher should not be included in request processing
    match (OK),            // associated should be included in request processing
    abort_ok (OK),         // abort request processing, return empty 200 OK response to client
    abort_not_found (NOT_FOUND);  // abort request processing, return empty 404 Not Found response to client

    @JsonCreator public static FilterMatchDecision fromString (String v) { return enumFromString(FilterMatchDecision.class, v); }

    @Getter private final int httpStatusCode;
    public int httpStatus() { return getHttpStatusCode(); }

}
