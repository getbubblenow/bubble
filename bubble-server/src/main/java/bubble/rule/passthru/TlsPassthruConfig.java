/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.passthru;

import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.cobbzilla.util.cache.AutoRefreshingReference;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.string.StringUtil;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static bubble.rule.passthru.FlexFeed.EMPTY_FLEX_FEEDS;
import static bubble.rule.passthru.TlsPassthruFeed.EMPTY_PASSTHRU_FEEDS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpUtil.getUrlInputStream;
import static org.cobbzilla.util.string.ValidationRegexes.HOST;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Slf4j @Accessors(chain=true)
public class TlsPassthruConfig {

    public static final long DEFAULT_TLS_FEED_REFRESH_INTERVAL = HOURS.toMillis(1);
    public static final long DEFAULT_FLEX_FEED_REFRESH_INTERVAL = HOURS.toMillis(1);
    public static final String FEED_NAME_PREFIX = "# Name:";

    @Getter @Setter private String[] passthruFqdnList;
    public boolean hasPassthruFqdnList() { return !empty(passthruFqdnList); }
    public boolean hasPassthruFqdn(String fqdn) { return hasPassthruFqdnList() && ArrayUtils.indexOf(passthruFqdnList, fqdn) != -1; }

    public TlsPassthruConfig addPassthruFqdn(String fqdn) {
        return setPassthruFqdnList(Arrays.stream(ArrayUtil.append(passthruFqdnList, fqdn)).collect(Collectors.toSet()).toArray(String[]::new));
    }

    public TlsPassthruConfig removePassthruFqdn(String id) {
        return !hasPassthruFqdnList() ? this :
                setPassthruFqdnList(Arrays.stream(getPassthruFqdnList())
                        .filter(fqdn -> !fqdn.equalsIgnoreCase(id.trim()))
                        .toArray(String[]::new));
    }

    @Getter @Setter private TlsPassthruFeed[] passthruFeedList;
    public boolean hasPassthruFeedList() { return !empty(passthruFeedList); }
    public boolean hasPassthruFeed(TlsPassthruFeed feed) {
        return hasPassthruFeedList() && Arrays.stream(passthruFeedList).anyMatch(f -> f.getPassthruFeedUrl().equals(feed.getPassthruFeedUrl()));
    }

    public TlsPassthruConfig addPassthruFeed(TlsPassthruFeed feed) {
        final Set<TlsPassthruFeed> feeds = getPassthruFeedSet();
        if (empty(feeds)) return setPassthruFeedList(new TlsPassthruFeed[] {feed});
        feeds.add(feed);
        return setPassthruFeedList(feeds.toArray(EMPTY_PASSTHRU_FEEDS));
    }

    public TlsPassthruConfig removePassthruFeed(String id) {
        return setPassthruFeedList(getPassthruFeedSet().stream()
                .filter(feed -> !feed.getId().equals(id))
                .toArray(TlsPassthruFeed[]::new));
    }

    private final Map<String, Set<String>> recentFeedValues = new HashMap<>();

    @JsonIgnore public Set<TlsPassthruFeed> getPassthruFeedSet() {
        final TlsPassthruFeed[] feedList = getPassthruFeedList();
        return !empty(feedList) ? Arrays.stream(feedList)
                .collect(Collectors.toCollection(TreeSet::new)) : Collections.emptySet();
    }

    @Getter @Setter private String[] flexFqdnList;
    public boolean hasFlexFqdnList () { return !empty(flexFqdnList); }
    public boolean hasFlexFqdn(String flexFqdn) { return hasFlexFqdnList() && ArrayUtils.indexOf(flexFqdnList, flexFqdn) != -1; }

    public TlsPassthruConfig addFlexFqdn(String flexFqdn) {
        return setFlexFqdnList(Arrays.stream(ArrayUtil.append(flexFqdnList, flexFqdn)).collect(Collectors.toSet()).toArray(String[]::new));
    }

    public TlsPassthruConfig removeFlexFqdn(String id) {
        return !hasFlexFqdnList() ? this :
                setFlexFqdnList(Arrays.stream(getFlexFqdnList())
                        .filter(flexFqdn -> !flexFqdn.equalsIgnoreCase(id.trim()))
                        .toArray(String[]::new));
    }

    @Getter @Setter private FlexFeed[] flexFeedList;
    public boolean hasFlexFeedList () { return !empty(flexFeedList); }
    public boolean hasFlexFeed (FlexFeed flexFeed) {
        return hasFlexFeedList() && Arrays.stream(flexFeedList).anyMatch(f -> f.getFlexFeedUrl().equals(flexFeed.getFlexFeedUrl()));
    }

    public TlsPassthruConfig addFlexFeed(FlexFeed flexFeed) {
        final Set<FlexFeed> flexFeeds = getFlexFeedSet();
        if (empty(flexFeeds)) return setFlexFeedList(new FlexFeed[] {flexFeed});
        flexFeeds.add(flexFeed);
        return setFlexFeedList(flexFeeds.toArray(EMPTY_FLEX_FEEDS));
    }

    public TlsPassthruConfig removeFlexFeed(String id) {
        return setFlexFeedList(getFlexFeedSet().stream()
                .filter(flexFeed -> !flexFeed.getId().equals(id))
                .toArray(FlexFeed[]::new));
    }

    private final Map<String, Set<String>> recentFlexFeedValues = new HashMap<>();

    @JsonIgnore public Set<FlexFeed> getFlexFeedSet() {
        final FlexFeed[] flexFeedList = getFlexFeedList();
        return !empty(flexFeedList) ? Arrays.stream(flexFeedList).collect(Collectors.toCollection(TreeSet::new)) : Collections.emptySet();
    }

    @ToString
    private static class TlsPassthruMatcher {
        @Getter @Setter private String fqdn;
        @Getter @Setter private Pattern fqdnPattern;
        public boolean hasPattern () { return fqdnPattern != null; }
        public boolean fqdnOnly () { return !hasPattern(); }
        public TlsPassthruMatcher (String fqdn) {
            this.fqdn = fqdn;
            if (fqdn.startsWith("/") && fqdn.endsWith("/")) {
                this.fqdnPattern = Pattern.compile(fqdn.substring(1, fqdn.length()-1), CASE_INSENSITIVE);
            } else if (fqdn.startsWith("*.")) {
                this.fqdnPattern = Pattern.compile("("+HOST+"\\.)?"+Pattern.quote(fqdn.substring(2)), CASE_INSENSITIVE);
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
        final Set<TlsPassthruMatcher> set = loadFeeds(this.passthruFeedList, this.passthruFqdnList, this.recentFeedValues);
        if (log.isDebugEnabled()) log.debug("loadPassthruSet: returning set: "+StringUtil.toString(set, ", ")+" -- fqdnList="+Arrays.toString(this.passthruFqdnList));
        return set;
    }

    @JsonIgnore @Getter(lazy=true) private final AutoRefreshingReference<Set<TlsPassthruMatcher>> flexSetRef = new AutoRefreshingReference<>() {
        @Override public Set<TlsPassthruMatcher> refresh() { return loadFlexSet(); }
        // todo: load refresh interval from config. implement a config view with an action to set it
        @Override public long getTimeout() { return DEFAULT_FLEX_FEED_REFRESH_INTERVAL; }
    };
    @JsonIgnore public Set<TlsPassthruMatcher> getFlexSet() { return getFlexSetRef().get(); }

    @JsonIgnore public Set<String> getFlexDomains() {
        return getFlexSetRef().get().stream()
                .filter(TlsPassthruMatcher::fqdnOnly)
                .map(TlsPassthruMatcher::getFqdn)
                .collect(Collectors.toSet());
    }

    private Set<TlsPassthruMatcher> loadFlexSet() {
        final Set<TlsPassthruMatcher> set = loadFeeds(this.flexFeedList, this.flexFqdnList, this.recentFlexFeedValues);
        if (log.isDebugEnabled()) log.debug("loadPassthruSet: returning fqdnList: "+StringUtil.toString(set, ", "));
        return set;
    }

    private Set<TlsPassthruMatcher> loadFeeds(BasePassthruFeed[] feedList, String[] fqdnList, Map<String, Set<String>> recentValues) {
        final Set<TlsPassthruMatcher> set = new HashSet<>();
        if (!empty(fqdnList)) {
            for (String val : fqdnList) {
                set.add(new TlsPassthruMatcher(val));
            }
        }
        if (!empty(feedList)) {
            // put in a set to avoid duplicate URLs
            for (BasePassthruFeed feed : new HashSet<>(Arrays.asList(feedList))) {
                final TlsPassthruFeed loaded = loadFeed(feed.getFeedUrl());

                // set name if found in special comment
                if (!feed.hasFeedName() && loaded.hasFeedName()) feed.setFeedName(loaded.getPassthruFeedName());

                // add to set if anything was found
                if (loaded.hasFqdnList()) recentValues.put(feed.getFeedUrl(), loaded.getPassthruFqdnList());
            }
        }
        for (String val : recentValues.values().stream().flatMap(Collection::stream).collect(Collectors.toSet())) {
            set.add(new TlsPassthruMatcher(val));
        }
        return set;
    }

    public TlsPassthruFeed loadFeed(String url) {
        final TlsPassthruFeed loaded = new TlsPassthruFeed().setPassthruFeedUrl(url);
        try (final InputStream in = getUrlInputStream(url)) {
            final List<String> lines = StringUtil.split(IOUtils.toString(in), "\r\n");
            final Set<String> fqdnList = new HashSet<>();
            for (String line : lines) {
                final String trimmed = line.trim();
                if (trimmed.length() == 0) continue;
                if (trimmed.startsWith("#")) {
                    if (!loaded.hasFeedName() && trimmed.toLowerCase().startsWith(FEED_NAME_PREFIX.toLowerCase())) {
                        final String name = trimmed.substring(FEED_NAME_PREFIX.length()).trim();
                        if (log.isTraceEnabled()) log.trace("loadFeed("+url+"): setting name="+name+" from special comment: "+trimmed);
                        loaded.setPassthruFeedName(name);
                    } else {
                        if (log.isDebugEnabled()) log.debug("loadFeed("+url+"): ignoring comment: "+trimmed);
                    }
                } else {
                    fqdnList.add(trimmed);
                }
            }
            loaded.setPassthruFqdnList(fqdnList);
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

    public boolean isFlex(String fqdn) {
        for (TlsPassthruMatcher match : getFlexSet()) {
            if (match.matches(fqdn)) return true;
        }
        return false;
    }

}
