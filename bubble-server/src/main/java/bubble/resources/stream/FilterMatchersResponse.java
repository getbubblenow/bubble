/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.stream;

import bubble.model.app.AppMatcher;
import bubble.rule.FilterMatchDecision;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.model.NamedEntity.names;

@NoArgsConstructor @Accessors(chain=true) @Slf4j
public class FilterMatchersResponse {

    public static final FilterMatchersResponse NO_MATCHERS = new FilterMatchersResponse().setDecision(FilterMatchDecision.no_match);
    public static final FilterMatchersResponse ABORT_OK = new FilterMatchersResponse().setDecision(FilterMatchDecision.abort_ok);
    public static final FilterMatchersResponse ABORT_NOT_FOUND = new FilterMatchersResponse().setDecision(FilterMatchDecision.abort_not_found);
    public static final FilterMatchersResponse PASS_THRU = new FilterMatchersResponse().setDecision(FilterMatchDecision.pass_thru);

    @Getter @Setter private FilterMatchersRequest request;
    public boolean hasRequest () { return request != null; }

    @Getter @Setter private FilterMatchDecision decision;

    @Getter @Setter private List<AppMatcher> matchers;
    public boolean hasMatchers() { return !empty(matchers); }

    public boolean hasRequestCheckMatchers() {
        return hasMatchers() && getMatchers().stream().anyMatch(AppMatcher::requestCheck);
    }

    public boolean hasRequestModifiers() {
        return hasMatchers() && getMatchers().stream().anyMatch(AppMatcher::requestModifier);
    }

    public FilterMatchersResponse setRequestId(String requestId) {
        if (request == null) {
            if (log.isInfoEnabled()) log.info("setRequestId("+requestId+"): request is null, cannot set");
        } else {
            request.setRequestId(requestId);
        }
        return this;
    }

    public boolean hasAbort() {
        return decision == FilterMatchDecision.abort_ok || decision == FilterMatchDecision.abort_not_found;
    }

    public int httpStatus() { return decision.httpStatus(); }

    @Override public String toString () {
        return "FilterMatchersResponse{"+decision+(hasMatchers() ? ", matchers="+names(matchers) : "")+"}";
    }

    @JsonIgnore public String getAccount() { return hasMatchers() ? getMatchers().get(0).getAccount() : null; }

}
