/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.analytics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.string.StringUtil;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class TrafficAnalyticsFilterPattern implements Comparable<TrafficAnalyticsFilterPattern> {

    public String getId() { return analyticsFilter; }
    public void setId(String id) {} // noop

    @Getter @Setter private String analyticsFilter;

    @JsonIgnore public String getCanonicalName () { return empty(analyticsFilter) ? "" : StringUtil.safeFunctionName(analyticsFilter.toLowerCase()); }

    public TrafficAnalyticsFilterPattern (String pattern) { this.analyticsFilter = pattern; }

    @Override public int compareTo(TrafficAnalyticsFilterPattern o) { return getCanonicalName().compareTo(o.getCanonicalName()); }

}
