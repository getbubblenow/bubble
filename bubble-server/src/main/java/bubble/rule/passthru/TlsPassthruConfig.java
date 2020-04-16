/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.passthru;

import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.cache.AutoRefreshingReference;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.string.StringUtil;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static bubble.rule.passthru.TlsPassthruFeed.EMPTY_FEEDS;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpUtil.getUrlInputStream;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Slf4j @Accessors(chain=true)
public class TlsPassthruConfig {

    public static final long DEFAULT_TLS_FEED_REFRESH_INTERVAL = HOURS.toMillis(1);
    public static final String FEED_NAME_PREFIX = "# Name:";

    @Getter @Setter private String[] fqdnList;
    public boolean hasFqdnList () { return !empty(fqdnList); }

    public TlsPassthruConfig addFqdn(String fqdn) {
        return setFqdnList(Arrays.stream(ArrayUtil.append(fqdnList, fqdn)).collect(Collectors.toSet()).toArray(String[]::new));
    }

    public TlsPassthruConfig removeFqdn(String id) {
        return !hasFqdnList() ? this :
                setFqdnList(Arrays.stream(getFqdnList())
                        .filter(fqdn -> !fqdn.equalsIgnoreCase(id.trim()))
                        .toArray(String[]::new));
    }

    @Getter @Setter private TlsPassthruFeed[] feedList;
    public boolean hasFeedList () { return !empty(feedList); }

    public TlsPassthruConfig addFeed(TlsPassthruFeed feed) {
        final Set<TlsPassthruFeed> feeds = getFeedSet();
        if (empty(feeds)) return setFeedList(new TlsPassthruFeed[] {feed});
        feeds.add(feed);
        return setFeedList(feeds.toArray(EMPTY_FEEDS));
    }

    public TlsPassthruConfig removeFeed(String id) {
        return setFeedList(getFeedSet().stream()
                .filter(feed -> !feed.getId().equals(id))
                .toArray(TlsPassthruFeed[]::new));
    }

    private Map<String, Set<String>> recentFeedValues = new HashMap<>();

    @JsonIgnore public Set<TlsPassthruFeed> getFeedSet() {
        final TlsPassthruFeed[] feedList = getFeedList();
        return !empty(feedList) ? Arrays.stream(feedList).collect(Collectors.toCollection(TreeSet::new)) : Collections.emptySet();
    }

    private static class TlsPassthruMatcher {
        @Getter @Setter private String fqdn;
        @Getter @Setter private Pattern fqdnPattern;
        public boolean hasPattern () { return fqdnPattern != null; }
        public TlsPassthruMatcher (String fqdn) {
            this.fqdn = fqdn;
            if (fqdn.startsWith("/") && fqdn.endsWith("/")) {
                this.fqdnPattern = Pattern.compile(fqdn.substring(1, fqdn.length()-1), Pattern.CASE_INSENSITIVE);
            }
        }
        public boolean matches (String val) {
            return hasPattern() ? fqdnPattern.matcher(val).matches() : fqdn.equals(val);
        }
    }

    @JsonIgnore @Getter(lazy=true) private final AutoRefreshingReference<Set<TlsPassthruMatcher>> passthruSetRef = new AutoRefreshingReference<>() {
        @Override public Set<TlsPassthruMatcher> refresh() { return loadPassthruSet(); }
        // todo: load refresh interval from config. implement a config view with an action to set it
        @Override public long getTimeout() { return DEFAULT_TLS_FEED_REFRESH_INTERVAL; }
    };
    @JsonIgnore public Set<TlsPassthruMatcher> getPassthruSet() { return getPassthruSetRef().get(); }

    private Set<TlsPassthruMatcher> loadPassthruSet() {
        final Set<TlsPassthruMatcher> set = new HashSet<>();
        if (hasFqdnList()) {
            for (String val : getFqdnList()) {
                set.add(new TlsPassthruMatcher(val));
            }
        }
        if (hasFeedList()) {
            // put in a set to avoid duplicate URLs
            for (TlsPassthruFeed feed : new HashSet<>(Arrays.asList(feedList))) {
                final TlsPassthruFeed loaded = loadFeed(feed.getFeedUrl());

                // set name if found in special comment
                if (!feed.hasFeedName() && loaded.hasFeedName()) feed.setFeedName(loaded.getFeedName());

                // add to set if anything was found
                if (loaded.hasFqdnList()) recentFeedValues.put(feed.getFeedUrl(), loaded.getFqdnList());
            }
        }
        for (String val : recentFeedValues.values().stream().flatMap(Collection::stream).collect(Collectors.toSet())) {
            set.add(new TlsPassthruMatcher(val));
        }
        if (log.isDebugEnabled()) log.debug("loadPassthruSet: returning fqdnList: "+StringUtil.toString(set, ", "));
        return set;
    }

    public TlsPassthruFeed loadFeed(String url) {
        final TlsPassthruFeed loaded = new TlsPassthruFeed().setFeedUrl(url);
        try (final InputStream in = getUrlInputStream(url)) {
            final List<String> lines = StringUtil.split(IOUtils.toString(in), "\r\n");
            final Set<String> fqdnList = new HashSet<>();
            for (String line : lines) {
                final String trimmed = line.trim();
                if (trimmed.length() == 0) continue;
                if (trimmed.startsWith("#")) {
                    if (!loaded.hasFeedName() && trimmed.toLowerCase().startsWith(FEED_NAME_PREFIX.toLowerCase())) {
                        final String name = trimmed.substring(FEED_NAME_PREFIX.length()).trim();
                        if (log.isDebugEnabled()) log.debug("loadFeed("+url+"): setting name="+name+" from special comment: "+trimmed);
                        loaded.setFeedName(name);
                    } else {
                        if (log.isDebugEnabled()) log.debug("loadFeed("+url+"): ignoring comment: "+trimmed);
                    }
                } else {
                    fqdnList.add(trimmed);
                }
            }
            loaded.setFqdnList(fqdnList);
        } catch (Exception e) {
            reportError("loadFeed("+url+"): "+shortError(e), e);
        }
        return loaded;
    }

    public boolean isPassthru(String fqdn) {
        for (TlsPassthruMatcher match : getPassthruSet()) {
            if (match.matches(fqdn)) return true;
        }
        return false;
    }

}
