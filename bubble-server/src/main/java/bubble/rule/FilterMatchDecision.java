package bubble.rule;

import com.fasterxml.jackson.annotation.JsonCreator;

import static bubble.ApiConstants.enumFromString;

public enum FilterMatchDecision {

    no_match,         // associated matcher should not be included in request processing
    match,            // associated should be included in request processing
    abort_ok,         // abort request processing, return empty 200 OK response to client
    abort_not_found;  // abort request processing, return empty 404 Not Found response to client

    @JsonCreator public static FilterMatchDecision fromString (String v) { return enumFromString(FilterMatchDecision.class, v); }

}
