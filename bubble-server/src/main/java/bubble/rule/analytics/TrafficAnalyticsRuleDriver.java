/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.rule.analytics;

import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.device.Device;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.AbstractAppRuleDriver;
import bubble.rule.FilterMatchDecision;
import bubble.service.stream.AppRuleHarness;
import lombok.Getter;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

public class TrafficAnalyticsRuleDriver extends AbstractAppRuleDriver {

    private static final long ANALYTICS_EXPIRATION = DAYS.toMillis(32);
    private static final long RECENT_TRAFFIC_EXPIRATION = HOURS.toSeconds(1);
    public static final String FQDN_SEP = "@";

    public static final String RECENT_TRAFFIC_PREFIX = TrafficAnalyticsRuleDriver.class.getSimpleName() + ".recent";
    public static final String PREFIX_HOURLY = "hourly_";
    public static final String PREFIX_DAILY = "daily_";

    @Getter(lazy=true) private final RedisService recentTraffic = redis.prefixNamespace(RECENT_TRAFFIC_PREFIX);

    @Override public FilterMatchDecision preprocess(AppRuleHarness ruleHarness,
                                                    FilterMatchersRequest filter,
                                                    Account account,
                                                    Device device,
                                                    Request req,
                                                    ContainerRequest request) {
        final String app = ruleHarness.getRule().getApp();
        final String site = ruleHarness.getMatcher().getSite();
        final String fqdn = filter.getFqdn();

        final TrafficRecord rec = new TrafficRecord(filter, account, device);
        recordRecentTraffic(rec);
        incrementCounters(account, device, app, site, fqdn);
        return FilterMatchDecision.no_match; // we are done, don't need to look at/modify stream
    }

    public void recordRecentTraffic(TrafficRecord rec) { recordRecentTraffic(rec, getRecentTraffic()); }

    public static void recordRecentTraffic(TrafficRecord rec, RedisService recentTraffic) {
        recentTraffic.set(now() + "_" + randomAlphanumeric(10), json(rec), EX, RECENT_TRAFFIC_EXPIRATION);
    }

    public void incrementCounters(Account account, Device device, String app, String site, String fqdn) {
        incr(account, device, app, site, fqdn, PREFIX_HOURLY, DATE_FORMAT_YYYY_MM_DD_HH.print(now()));
        incr(account, null, app, site, fqdn, PREFIX_HOURLY, DATE_FORMAT_YYYY_MM_DD_HH.print(now()));
        incr(account, device, app, site, fqdn, PREFIX_DAILY, DATE_FORMAT_YYYY_MM_DD.print(now()));
        incr(account, null, app, site, fqdn, PREFIX_DAILY, DATE_FORMAT_YYYY_MM_DD.print(now()));
    }

    // we use synchronized here but in a multi-node scenario this is not sufficient, we still have some risk
    // of simultaneous requests -- but because we lookup the value and increment it, hopefully the worst case
    // is that we miss a few increments, hopefully not a huge deal in the big picture. The real bad case is
    // if the underlying db driver gets into an upset state because of the concurrent updates. We will cross
    // that bridge when we get to it.
    protected synchronized void incr(Account account, Device device, String app, String site, String fqdn, String prefix, String tstamp) {
        final String key = fqdn + FQDN_SEP + prefix + tstamp;
        final AppData found = appDataDAO.findByAppAndSiteAndKeyAndDevice(app, site, key, device == null ? null : device.getUuid());
        if (found == null) {
            appDataDAO.create(new AppData()
                    .setApp(app)
                    .setSite(matcher.getSite())
                    .setMatcher(matcher.getUuid())
                    .setKey(key)
                    .setMeta1(tstamp)
                    .setMeta2(fqdn)
                    .setAccount(account.getUuid())
                    .setDevice(device == null ? null : device.getUuid())
                    .setExpiration(now() + ANALYTICS_EXPIRATION)
                    .setData("1"));
        } else {
            appDataDAO.update(found.incrData());
        }
    }

}
