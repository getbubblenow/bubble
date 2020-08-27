/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.block;

import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.FilterMatchDecision;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.cobbzilla.util.collection.ExpirationEvictionPolicy;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.http.HttpSchemes.stripScheme;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

@Service @Slf4j
public class BlockStatsService {

    private final Map<String, BlockStatRecord> records
            = new ExpirationMap<>(200, MINUTES.toMillis(10), ExpirationEvictionPolicy.atime);

    private final String[] EXCLUDE_FQDNS = {
            "detectportal.firefox.com", "push.services.mozilla.com",
            "spocs.getpocket.com", "img-getpocket.cdn.mozilla.net",
            "incoming.telemetry.mozilla.org", "safebrowsing.googleapis.com"
    };

    public void flush () { records.clear(); }

    public void record(FilterMatchersRequest filter, FilterMatchDecision decision) {
        if (excludeFqdn(filter.getFqdn())) {
            if (log.isDebugEnabled()) log.debug("record: excluding fqdn="+filter.getFqdn());
            return;
        }
        if (log.isDebugEnabled()) log.debug("record: >>>>> processing URL="+filter.getUrl()+" REFERER="+filter.getReferer()+" DECISION="+decision);
        addTopLevelRecord(filter, decision);
        if (!filter.hasReferer()) {
            // this must be a top-level request
            if (log.isDebugEnabled()) log.debug("record: (no referer) added top-level record for device="+filter.getDevice()+"/userAgent="+filter.getUserAgent()+"/url="+filter.getUrl());

        } else {
            // find match based on device + user-agent + referer
            final String cacheKey = getRefererCacheKey(filter);
            BlockStatRecord rec = records.get(cacheKey);
            if (rec == null) {
                // try fqdn
                rec = records.get(getFqdnKey(filter.getRefererFqdn(), filter.getUserAgent()));
                if (rec == null) {
                    if (log.isWarnEnabled()) log.warn("record: parent not found for device=" + filter.getDevice() + "/userAgent=" + filter.getUserAgent() + "/referer=" + filter.getReferer());
                    return;
                }
            }
            final BlockStatRecord childRec = new BlockStatRecord(filter, decision);
            rec.addChild(childRec);
            records.put(getUrlCacheKey(filter), childRec);
            records.put(getFqdnKey(filter.getFqdn(), filter.getUserAgent()), childRec);
            if (log.isDebugEnabled()) log.debug("record: child("+getUrlCacheKey(filter)+", "+filter.getRequestId()+")= newRec="+json(childRec)+",\nparent="+json(rec));
        }
    }

    public void addTopLevelRecord(FilterMatchersRequest filter, FilterMatchDecision decision) {
        final BlockStatRecord newRec = new BlockStatRecord(filter, decision);
        records.put(getUrlCacheKey(filter), newRec);
        records.put(getFqdnKey(filter.getFqdn(), filter.getUserAgent()), newRec);
        records.put(filter.getRequestId(), newRec);
    }

    private boolean excludeFqdn(String fqdn) { return ArrayUtils.contains(EXCLUDE_FQDNS, fqdn); }

    public String getFqdnKey(String fqdn, String userAgent) { return fqdn+"\t"+userAgent; }

    public String getRefererCacheKey(FilterMatchersRequest filter) {
        return filter.getDevice()+"\t"+filter.getUserAgent()+"\t"+stripScheme(filter.getReferer());
    }

    public String getUrlCacheKey(FilterMatchersRequest filter) {
        return filter.getDevice()+"\t"+filter.getUserAgent()+"\t"+stripScheme(filter.getUrl());
    }

    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService summaryCache =
            redis == null ? null : redis.prefixNamespace(getClass().getSimpleName()+"_summaryCache");

    public BlockStatsSummary getSummary(String requestId) {
        final BlockStatRecord stat = records.get(requestId);
        final RedisService cache = getSummaryCache();
        if (stat == null) {
            final String summaryJson = cache == null ? null : cache.get(requestId);
            if (summaryJson == null) {
                if (log.isInfoEnabled()) log.info("getSummary("+requestId+") no summary found");
                return null;
            } else {
                if (log.isDebugEnabled()) log.debug("getSummary("+requestId+") returning cached summary: "+summaryJson);
                return json(summaryJson, BlockStatsSummary.class);
            }
        }
        final BlockStatsSummary summary = stat.summarize();
        if (log.isDebugEnabled()) log.debug("getSummary("+requestId+") returning (and caching) live summary="+json(summary)+" for record="+json(stat));
        if (cache != null) cache.set(requestId, json(summary, COMPACT_MAPPER), EX, HOURS.toSeconds(24));
        return summary;
    }

}
