package bubble.rule.passthru;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

@NoArgsConstructor @Accessors(chain=true) @EqualsAndHashCode(of="feedUrl")
public class TlsPassthruFeed implements Comparable<TlsPassthruFeed> {

    public static final TlsPassthruFeed[] EMPTY_FEEDS = new TlsPassthruFeed[0];

    public String getId() { return sha256_hex(getFeedUrl()); }
    public void setId(String id) {} // noop

    @Getter @Setter private String feedName;
    public boolean hasFeedName() { return !empty(feedName); }

    @Getter @Setter private String feedUrl;

    @JsonIgnore @Getter @Setter private Set<String> fqdnList;
    public boolean hasFqdnList () { return !empty(fqdnList); }

    public TlsPassthruFeed(String url) { setFeedUrl(url); }

    public TlsPassthruFeed(TlsPassthruFeed feed) { copy(this, feed); }

    @Override public int compareTo(TlsPassthruFeed o) {
        return getFeedUrl().toLowerCase().compareTo(o.getFeedUrl().toLowerCase());
    }

}
