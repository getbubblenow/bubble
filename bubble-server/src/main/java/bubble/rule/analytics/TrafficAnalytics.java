package bubble.rule.analytics;

import bubble.dao.app.AppDataDAO;
import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.service.stream.AppRuleHarness;
import bubble.rule.AbstractAppRuleDriver;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH;

public class TrafficAnalytics extends AbstractAppRuleDriver {

    private static final long ANALYTICS_EXPIRATION = DAYS.toMillis(30);

    @Autowired private AppDataDAO appDataDAO;

    @Override public boolean preprocess(AppRuleHarness ruleHarness, FilterMatchersRequest filter, Account account, Request req, ContainerRequest request) {
        final String app = ruleHarness.getRule().getApp();
        final String site = ruleHarness.getMatcher().getSite();
        final String fqdn = filter.getFqdn();
        // todo: determine device, use that in key
        final String key = fqdn + "/" + DATE_FORMAT_YYYY_MM_DD_HH.print(now());
        final AppData found = appDataDAO.findByAppAndSiteAndKey(app, site, key);
        if (found == null) {
            appDataDAO.create(new AppData()
                    .setApp(app)
                    .setSite(matcher.getSite())
                    .setMatcher(matcher.getUuid())
                    .setKey(key)
                    .setAccount(account.getUuid())
                    .setExpiration(now() + ANALYTICS_EXPIRATION)
                    .setData("1"));
        } else {
            appDataDAO.update(found.incrData());
        }
        return true;
    }

}
