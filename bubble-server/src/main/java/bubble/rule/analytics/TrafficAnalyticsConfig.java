/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.analytics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true) @ToString(of="filterPatterns")
public class TrafficAnalyticsConfig {

    @Getter @Setter private String[] filterPatterns;

    @JsonIgnore public Set<TrafficAnalyticsFilterPattern> getPatterns () {
        final Set<TrafficAnalyticsFilterPattern> set = new TreeSet<>();
        if (!empty(filterPatterns)) {
            set.addAll(Arrays.stream(filterPatterns)
                    .map(TrafficAnalyticsFilterPattern::new)
                    .collect(Collectors.toList()));
        }
        return set;
    }

    public TrafficAnalyticsConfig addFilter(String filter) {
        final Set<TrafficAnalyticsFilterPattern> patterns = getPatterns();
        patterns.add(new TrafficAnalyticsFilterPattern(filter));
        return setFilterPatterns(patterns.stream()
                .map(TrafficAnalyticsFilterPattern::getAnalyticsFilter)
                .toArray(String[]::new));
    }

    public TrafficAnalyticsConfig removeFilter(String id) {
        if (!empty(filterPatterns)) {
            final Set<TrafficAnalyticsFilterPattern> patterns = getPatterns();
            patterns.remove(new TrafficAnalyticsFilterPattern(id));
            setFilterPatterns(patterns.stream()
                    .map(TrafficAnalyticsFilterPattern::getAnalyticsFilter)
                    .toArray(String[]::new));
        }
        return this;
    }

    @JsonIgnore @Getter(lazy=true) private final List<Pattern> regexes = initRegexes();
    private List<Pattern> initRegexes() {
        final List<Pattern> patterns = new ArrayList<>();
        if (!empty(filterPatterns)) {
            for (String pattern : filterPatterns) patterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
        }
        return patterns;
    }

    public boolean shouldSkip(String url) {
        if (!empty(filterPatterns)) {
            for (Pattern pattern : getRegexes()) {
                if (pattern.matcher(url).find()) return true;
            }
        }
        return false;
    }

}
