/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.http.HttpUtil;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpSchemes.stripScheme;

@NoArgsConstructor @Accessors(chain=true)
public class FilterMatchersRequest {

    @Getter @Setter private String requestId;
    public boolean hasRequestId () { return !empty(requestId); }

    @Getter @Setter private String device;
    public boolean hasDevice() { return !empty(device); }

    @Getter @Setter private String fqdn;
    @Getter @Setter private String uri;
    @Getter @Setter private String userAgent;
    @JsonIgnore public boolean isBrowser () { return HttpUtil.isBrowser(getUserAgent()); }

    @Getter @Setter private String referer;
    public boolean hasReferer () { return !empty(referer) && !referer.equals("NONE"); }

    public String referrerUrlOnly () {
        if (!hasReferer()) return null;
        final int qPos = referer.indexOf("?");
        return qPos == -1 ? referer : referer.substring(0, qPos);
    }

    @JsonIgnore public String getRefererFqdn () {
        if (!hasReferer()) return null;
        final String base = stripScheme(referer);
        final int slashPos = base.indexOf('/');
        return slashPos == -1 ? base : base.substring(0, slashPos);
    }

    @Getter @Setter private String clientAddr;
    public boolean hasClientAddr() { return !empty(clientAddr); }

    @Getter @Setter private String serverAddr;
    public boolean hasServerAddr() { return !empty(serverAddr); }

    // note: we do *not* include the requestId in the cache, if we did then the
    // FilterHttpResource.matchersCache cache would be useless, since every cache entry would be unique
    public String cacheKey() { return hashOf(device, fqdn, uri, userAgent, referer, clientAddr, serverAddr); }

    @JsonIgnore public String getUrl() { return fqdn + uri; }

}
