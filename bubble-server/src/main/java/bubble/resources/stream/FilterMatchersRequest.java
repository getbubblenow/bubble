/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.resources.stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;

@NoArgsConstructor @Accessors(chain=true)
public class FilterMatchersRequest {

    @Getter @Setter private String requestId;
    public boolean hasRequestId () { return !empty(requestId); }

    @Getter @Setter private String device;
    public boolean hasDevice() { return !empty(device); }

    @Getter @Setter private String fqdn;
    @Getter @Setter private String uri;
    @Getter @Setter private String userAgent;

    @Getter @Setter private String referer;
    public boolean hasReferer () { return !empty(referer); }

    @Getter @Setter private String remoteAddr;
    public boolean hasRemoteAddr() { return !empty(remoteAddr); }

    // note: we do *not* include the requestId in the cache, if we did then the
    // FilterHttpResource.matchersCache cache would be useless, since every cache entry would be unique
    public String cacheKey() { return hashOf(device, fqdn, uri, userAgent, referer, remoteAddr); }

    @JsonIgnore public String getUrl() { return fqdn + uri; }

}
