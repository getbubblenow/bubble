package bubble.rule.bblock;

import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.device.Device;
import bubble.resources.stream.FilterMatchResponse;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.analytics.TrafficAnalytics;
import bubble.rule.bblock.spec.BlockDecision;
import bubble.rule.bblock.spec.BlockList;
import bubble.rule.bblock.spec.BlockListSource;
import bubble.service.stream.AppRuleHarness;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH;

@Slf4j
public class BubbleBlock extends TrafficAnalytics {

    private BubbleBlockConfig bubbleBlockConfig;

    private BlockList blockList = new BlockList();

    private static Map<String, BlockListSource> blockListCache = new ConcurrentHashMap<>();

    @Override public void init(JsonNode config, JsonNode userConfig, AppRule rule, AppMatcher matcher, Account account, Device device) {
        super.init(config, userConfig, rule, matcher, account, device);

        bubbleBlockConfig = json(json(config), BubbleBlockConfig.class);
        for (String listUrl : bubbleBlockConfig.getBlockLists()) {
            BlockListSource blockListSource = blockListCache.get(listUrl);
            if (blockListSource == null || blockListSource.age() > TimeUnit.DAYS.toMillis(5)) {
                try {
                    final BlockListSource newList = new BlockListSource()
                            .setUrl(listUrl)
                            .download();
                    blockListCache.put(listUrl, newList);
                    blockListSource = newList;
                } catch (Exception e) {
                    log.error("init: error downloading blocklist "+listUrl+": "+shortError(e));
                    continue;
                }
            }
            blockList.merge(blockListSource.getBlockList());
        }
    }

    @Override public FilterMatchResponse preprocess(AppRuleHarness ruleHarness,
                                                    FilterMatchersRequest filter,
                                                    Account account,
                                                    Device device,
                                                    Request req,
                                                    ContainerRequest request) {
        final String app = ruleHarness.getRule().getApp();
        final String site = ruleHarness.getMatcher().getSite();
        final String fqdn = filter.getFqdn();

        final BlockDecision decision = blockList.getDecision(filter.getFqdn(), filter.getUri());
        switch (decision.getDecisionType()) {
            case block:
                incr(account, device, app, site, fqdn, DATE_FORMAT_YYYY_MM_DD_HH.print(now()));
                incr(account, null, app, site, fqdn, DATE_FORMAT_YYYY_MM_DD_HH.print(now()));
                return FilterMatchResponse.ABORT_NOT_FOUND;  // block this request
            case allow: default:
                return FilterMatchResponse.NO_MATCH;
            case filter:
                return decision.getFilterMatchResponse();
        }
    }

    @Override public InputStream doFilterResponse(String requestId, InputStream in) {
        // todo : insert selector-based block JS
        return in;
    }
}
