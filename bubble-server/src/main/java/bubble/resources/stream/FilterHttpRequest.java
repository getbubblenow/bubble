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
import org.cobbzilla.util.http.HttpContentEncodingType;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class FilterHttpRequest {

    @Getter @Setter private String id;
    @Getter @Setter private FilterMatchersResponse matchersResponse;
    @Getter @Setter private Device device;
    @Getter @Setter private HttpContentEncodingType encoding;
    @Getter @Setter private Account account;
    @Getter @Setter private String contentType;

    @Getter @Setter private Long contentLength;
    public boolean hasContentLength () { return contentLength != null; }

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

}
