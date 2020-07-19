/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.stream;

import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.device.Device;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpContentEncodingType;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.string.StringUtil.ellipsis;

@NoArgsConstructor @Accessors(chain=true) @Slf4j
public class FilterHttpRequest {

    @Getter @Setter private String id;
    @Getter @Setter private FilterMatchersResponse matchersResponse;
    @Getter @Setter private Device device;
    @Getter @Setter private HttpContentEncodingType encoding;
    @Getter @Setter private Account account;
    @Getter @Setter private String contentType;

    @Getter @Setter private Long contentLength;
    public boolean hasContentLength () { return contentLength != null; }

    @Getter @Setter private String contentSecurityPolicy;
    public boolean hasContentSecurityPolicy () { return !empty(contentSecurityPolicy); }

    public static final Pattern NONCE_PATTERN = Pattern.compile("\\s+script-src\\s+.*'nonce-([^']+)'");

    @Getter(lazy=true) private final String scriptNonce = initScriptNonce();
    private String initScriptNonce () {
        log.info("initScriptNonce: finding script nonce in csp="+ellipsis(contentSecurityPolicy, 20));
        if (!hasContentSecurityPolicy()) return null;
        final Matcher matcher = NONCE_PATTERN.matcher(contentSecurityPolicy);
        if (matcher.find()) {
            log.info("initScriptNonce: found nonce='"+matcher.group(1)+"'");
            return matcher.group(1);
        }
        log.info("initScriptNonce: nonce not found");
        return null;
    }
    public boolean hasScriptNonce () { return hasContentSecurityPolicy() && !empty(getScriptNonce()); }

    public boolean hasMatcher (String matcherId) {
        if (empty(matcherId) || !hasMatchers()) return false;
        return matchersResponse.getMatchers().stream().anyMatch(m -> m.getUuid().equals(matcherId));
    }

    public boolean hasMatchers() { return matchersResponse != null && matchersResponse.hasMatchers(); }

    @JsonIgnore public List<AppMatcher> getMatchers() { return !hasMatchers() ? null : matchersResponse.getMatchers(); }

    @JsonIgnore public String getUrl() {
        return !hasMatchers() || !matchersResponse.hasRequest() ? null : matchersResponse.getRequest().getUrl();
    }

    public boolean hasApp(String appId) {
        if (!hasMatchers()) return false;
        for (AppMatcher m : getMatchers()) if (m.getApp().equals(appId)) return true;
        return false;
    }

    @JsonIgnore public String getMatcherNames() {
        return !hasMatchers() ? "no-matchers" : getMatchers().stream()
                .map(AppMatcher::getName)
                .collect(Collectors.joining(", "));
    }
}
