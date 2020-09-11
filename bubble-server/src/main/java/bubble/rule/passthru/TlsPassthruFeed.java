/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.passthru;

import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Set;

import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor @Accessors(chain=true)
public class TlsPassthruFeed extends BasePassthruFeed {

    public static final TlsPassthruFeed[] EMPTY_PASSTHRU_FEEDS = new TlsPassthruFeed[0];

    public String getPassthruFeedName () { return getFeedName(); }
    public TlsPassthruFeed setPassthruFeedName (String name) { return (TlsPassthruFeed) setFeedName(name); }

    public String getPassthruFeedUrl () { return getFeedUrl(); }
    public TlsPassthruFeed setPassthruFeedUrl (String url) { return (TlsPassthruFeed) setFeedUrl(url); }

    public Set<String> getPassthruFqdnList () { return getFqdnList(); }
    public TlsPassthruFeed setPassthruFqdnList (Set<String> set) { return (TlsPassthruFeed) setFqdnList(set); }

    public TlsPassthruFeed(String url) { super(url); }

    public TlsPassthruFeed(TlsPassthruFeed feed) { copy(this, feed); }

}
