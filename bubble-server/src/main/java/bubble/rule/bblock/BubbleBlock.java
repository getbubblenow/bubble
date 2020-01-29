package bubble.rule.bblock;

import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.PreprocessDecision;
import bubble.rule.analytics.TrafficAnalytics;
import bubble.service.stream.AppRuleHarness;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH;

public class BubbleBlock extends TrafficAnalytics {

    @Override public PreprocessDecision preprocess(AppRuleHarness ruleHarness,
                                                   FilterMatchersRequest filter,
                                                   Account account,
                                                   Device device,
                                                   Request req,
                                                   ContainerRequest request) {
        final String app = ruleHarness.getRule().getApp();
        final String site = ruleHarness.getMatcher().getSite();
        final String fqdn = filter.getFqdn();

        if (shouldBlock(account, device, filter)) {
            incr(account, device, app, site, fqdn, DATE_FORMAT_YYYY_MM_DD_HH.print(now()));
            incr(account, null, app, site, fqdn, DATE_FORMAT_YYYY_MM_DD_HH.print(now()));
            return PreprocessDecision.abort_not_found;  // block this request
        }
        return PreprocessDecision.no_match; // don't block, and don't process this matcher (we do not modify streams)
    }

    private boolean shouldBlock(Account account, Device device, FilterMatchersRequest filter) {
        return false;
    }

}
