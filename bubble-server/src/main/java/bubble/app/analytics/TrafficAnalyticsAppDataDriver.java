/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.app.analytics;

import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.app.AppSite;
import bubble.model.app.BubbleApp;
import bubble.model.app.config.AppDataDriverBase;
import bubble.model.app.config.AppDataView;
import bubble.model.device.Device;
import bubble.rule.TrafficRecord;
import bubble.rule.analytics.TrafficAnalyticsRuleDriver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.network.NetworkUtil;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.search.SearchBoundComparison;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.cobbzilla.wizard.model.search.SearchSort;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static bubble.rule.analytics.TrafficAnalyticsRuleDriver.*;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpContentTypes.TYPICAL_WEB_TYPES;
import static org.cobbzilla.util.http.HttpContentTypes.fileExt;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.PCT;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH;
import static org.cobbzilla.wizard.model.search.SearchBoundComparison.Constants.ILIKE_SEP;
import static org.cobbzilla.wizard.model.search.SearchField.OP_SEP;
import static org.cobbzilla.wizard.model.search.SortOrder.ASC;
import static org.cobbzilla.wizard.model.search.SortOrder.DESC;
import static org.cobbzilla.wizard.util.TestNames.*;

@Slf4j
public class TrafficAnalyticsAppDataDriver extends AppDataDriverBase {

    public static final String VIEW_recent = "recent";
    public static final String VIEW_last_24_hours = "last_24_hours";
    public static final String VIEW_last_7_days = "last_7_days";
    public static final String VIEW_last_30_days = "last_30_days";

    public static final SearchSort SORT_TSTAMP_DESC = new SearchSort("meta1", DESC);
    public static final SearchSort SORT_FQDN_CASE_INSENSITIVE_ASC = new SearchSort("meta2", ASC, "lower");

    public static final int MAX_RECENT_PAGE_SIZE = 50;

    @Getter(lazy=true) private final RedisService recentTraffic = redis.prefixNamespace(RECENT_TRAFFIC_PREFIX);

    @Override public SearchResults query(Account caller, Device device, BubbleApp app, AppSite site, AppDataView view, SearchQuery query) {

        if (configuration.testMode()) recordTestTraffic(caller, device);

        if (view.getName().equals(VIEW_recent)) {
            final RedisService traffic = getRecentTraffic();
            final TreeSet<String> keys = new TreeSet<>(Collections.reverseOrder());
            keys.addAll(traffic.keys("*"));
            final List<TrafficRecord> records = new ArrayList<>();
            int i = 0;
            if (query.getPageSize() > MAX_RECENT_PAGE_SIZE) {
                query.setPageSize(MAX_RECENT_PAGE_SIZE);
            }
            for (String key : keys) {
                if (i < query.getPageOffset()) {
                    i++;
                    continue;
                }
                if (i >= query.getPageEndOffset()) {
                    break;
                }
                final String json = traffic.get_withPrefix(key);
                if (json != null) {
                    try {
                        records.add(json(json, TrafficRecord.class));
                    } catch (Exception e) {
                        log.warn("query: error parsing TrafficRecord: "+shortError(e));
                    }
                }
                if (records.size() >= query.getPageSize()) {
                    log.info("query: max page size reached "+query.getPageSize()+", breaking");
                }
                i++;
            }
            log.info("query: VIEW_recent: returning "+records.size()+" / "+keys.size()+" recent traffic records");
            return new SearchResults(records, keys.size());
        }

        if (!query.hasBound("device")) {
            final String deviceBound = device == null
                    ? SearchBoundComparison.is_null.name() + OP_SEP
                    : SearchBoundComparison.eq.name() + OP_SEP + device.getUuid();
            query.setBound("device", deviceBound);
        }
        query.setBound("key", getKeyBound(view));
        if (!query.hasSorts()) {
            query.addSort(SORT_TSTAMP_DESC);
            query.addSort(SORT_FQDN_CASE_INSENSITIVE_ASC);
        }
        return processResults(super.query(caller, device, app, site, view, query));
    }

    private void recordTestTraffic(Account caller, Device device) {
        recordRecentTraffic(new TrafficRecord()
                        .setRequestTime(now())
                        .setIp(NetworkUtil.getFirstPublicIpv4())
                        .setFqdn(safeNationality()+".example.com")
                        .setUri("/traffic/"+safeColor()+"/"+ safeAnimal()
                                + fileExt(TYPICAL_WEB_TYPES[nextInt(0, TYPICAL_WEB_TYPES.length)]))
                        .setReferer("NONE")
                        .setAccountUuid(caller.getUuid())
                        .setAccountEmail(caller.getEmail())
                        .setDeviceUuid(device.getUuid())
                        .setDeviceName(device.getName()),
                getRecentTraffic());
    }

    private SearchResults processResults(SearchResults searchResults) {
        final List<TrafficAnalyticsData> data = new ArrayList<>();
        if (searchResults.hasResults()) {
            for (AppData result : (List<AppData>) searchResults.getResults()) {
                data.add(new TrafficAnalyticsData(result));
            }
        }
        return searchResults.setResults(data);
    }

    private String getKeyBound(AppDataView view) {
        final int limit;
        final DateTimeFormatter format;
        final TimeUnit increment;
        final String prefix;
        switch (view.getName()) {
            default:
            case VIEW_last_24_hours:
                prefix = TrafficAnalyticsRuleDriver.PREFIX_HOURLY;
                limit = 24;
                format = DATE_FORMAT_YYYY_MM_DD_HH;
                increment = HOURS;
                break;
            case VIEW_last_7_days:
                prefix = TrafficAnalyticsRuleDriver.PREFIX_DAILY;
                limit = 7;
                format = DATE_FORMAT_YYYY_MM_DD;
                increment = DAYS;
                break;
            case VIEW_last_30_days:
                prefix = TrafficAnalyticsRuleDriver.PREFIX_DAILY;
                limit = 30;
                format = DATE_FORMAT_YYYY_MM_DD;
                increment = DAYS;
                break;
        }

        final long now = now();
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i< limit; i++) {
            if (b.length() > 0) b.append(ILIKE_SEP);
            b.append(PCT + FQDN_SEP)
                    .append(prefix)
                    .append(format.print(new DateTime(now).withZone(DateTimeZone.UTC).plus(-1 * increment.toMillis(i))))
                    .append(PCT);
        }

        return SearchBoundComparison.like_any.name() + OP_SEP + b.toString();
    }

}
