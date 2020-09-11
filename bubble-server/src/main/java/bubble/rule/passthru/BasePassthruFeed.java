/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.passthru;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

@NoArgsConstructor @Accessors(chain=true) @EqualsAndHashCode(of="feedUrl")
public class BasePassthruFeed implements Comparable<BasePassthruFeed> {

    public BasePassthruFeed (String url) { setFeedUrl(url); }

    public String getId() { return sha256_hex(getFeedUrl()); }
    public void setId(String id) {} // noop

    @JsonIgnore @Getter @Setter private String feedName;
    public boolean hasFeedName() { return !empty(feedName); }

    @JsonIgnore @Getter @Setter private String feedUrl;

    @JsonIgnore @Getter @Setter private Set<String> fqdnList;
    public boolean hasFqdnList() { return !empty(fqdnList); }

    @Override public int compareTo(BasePassthruFeed o) {
        return getFeedUrl().toLowerCase().compareTo(o.getFeedUrl().toLowerCase());
    }

}
