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
public class FlexFeed extends BasePassthruFeed {

    public static final FlexFeed[] EMPTY_FLEX_FEEDS = new FlexFeed[0];

    public String getFlexFeedName () { return getFeedName(); }
    public FlexFeed setFlexFeedName (String name) { return (FlexFeed) setFeedName(name); }

    public String getFlexFeedUrl () { return getFeedUrl(); }
    public FlexFeed setFlexFeedUrl (String url) { return (FlexFeed) setFeedUrl(url); }

    public Set<String> getFlexFqdnList () { return getFqdnList(); }
    public FlexFeed setFlexFqdnList (Set<String> set) { return (FlexFeed) setFqdnList(set); }

    public FlexFeed(String url) { super(url); }

    public FlexFeed(FlexFeed feed) { copy(this, feed); }

}
