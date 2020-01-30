package bubble.resources.stream;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;

@NoArgsConstructor @Accessors(chain=true)
public class FilterMatchersRequest {

    @Getter @Setter private String requestId;
    @Getter @Setter private String fqdn;
    @Getter @Setter private String uri;
    @Getter @Setter private String userAgent;
    @Getter @Setter private String referer;
    @Getter @Setter private String remoteAddr;

    // note: we do *not* include the requestId in the cache, if we did then the
    // FilterHttpResource.matchersCache cache would be useless, since every cache entry would be unique
    public String cacheKey() { return hashOf(fqdn, uri, userAgent, referer, remoteAddr); }

}
