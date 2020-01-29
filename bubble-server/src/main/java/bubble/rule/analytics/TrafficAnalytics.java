package bubble.rule.analytics;

import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.device.Device;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.AbstractAppRuleDriver;
import bubble.service.stream.AppRuleHarness;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH;

public class TrafficAnalytics extends AbstractAppRuleDriver {

    private static final long ANALYTICS_EXPIRATION = DAYS.toMillis(32);

    public static final String FQDN_SEP = "@";

    @Override public boolean preprocess(AppRuleHarness ruleHarness,
                                        FilterMatchersRequest filter,
                                        Account account,
                                        Device device,
                                        Request req,
                                        ContainerRequest request) {
        final String app = ruleHarness.getRule().getApp();
        final String site = ruleHarness.getMatcher().getSite();
        final String fqdn = filter.getFqdn();

        incr(account, device, app, site, fqdn + FQDN_SEP + DATE_FORMAT_YYYY_MM_DD_HH.print(now()));
        incr(account, null, app, site, fqdn + FQDN_SEP + DATE_FORMAT_YYYY_MM_DD_HH.print(now()));
        return true;
    }

    // we use synchronized here but in a multi-node scenario this is not sufficient, we still have some risk
    // of simultaneous requests -- but because we lookup the value and increment it, hopefully the worst case
    // is that we miss a few increments, hopefully not a huge deal in the big picture. The real bad case is
    // if the underlying db driver gets into an upset state because of the concurrent updates. We will cross
    // that bridge when we get to it.
    private synchronized void incr(Account account, Device device, String app, String site, String key) {
        final AppData found = appDataDAO.findByAppAndSiteAndKeyAndDevice(app, site, key, device == null ? null : device.getUuid());
        if (found == null) {
            appDataDAO.create(new AppData()
                    .setApp(app)
                    .setSite(matcher.getSite())
                    .setMatcher(matcher.getUuid())
                    .setKey(key)
                    .setAccount(account.getUuid())
                    .setDevice(device == null ? null : device.getUuid())
                    .setExpiration(now() + ANALYTICS_EXPIRATION)
                    .setData("1"));
        } else {
            appDataDAO.update(found.incrData());
        }
    }

}
