package bubble.rule.bblock;

import bubble.abp.spec.BlockDecision;
import bubble.abp.spec.BlockList;
import bubble.abp.spec.BlockListSource;
import bubble.abp.spec.BlockSpec;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.device.Device;
import bubble.resources.stream.FilterMatchResponse;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.FilterMatchDecision;
import bubble.rule.analytics.TrafficAnalytics;
import bubble.service.stream.AppRuleHarness;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.ReaderInputStream;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.regex.RegexFilterReader;
import org.cobbzilla.util.io.regex.RegexReplacementFilter;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

@Slf4j
public class BubbleBlock extends TrafficAnalytics {

    private static final String META_BLOCK_FILTERS = "__bubble_block_filters";

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
                incrementCounters(account, device, app, site, fqdn);
                return FilterMatchResponse.ABORT_NOT_FOUND;  // block this request
            case allow: default:
                return FilterMatchResponse.NO_MATCH;
            case filter:
                return getFilterMatchResponse(decision);
        }
    }

    public FilterMatchResponse getFilterMatchResponse(BlockDecision decision) {
        switch (decision.getDecisionType()) {
            case block: return FilterMatchResponse.ABORT_NOT_FOUND;
            case allow: return FilterMatchResponse.NO_MATCH;
            case filter:
                final List<BlockSpec> specs = decision.getSpecs();
                if (empty(specs)) {
                    log.warn("getFilterMatchResponse: decision was 'filter' but no specs were found, returning no_match");
                    return FilterMatchResponse.NO_MATCH;
                } else {
                    return new FilterMatchResponse()
                            .setDecision(FilterMatchDecision.match)
                            .setMeta(new NameAndValue[]{new NameAndValue(META_BLOCK_FILTERS, json(specs))});
                }
        }
        return die("getFilterMatchResponse: invalid decisionType: "+decision.getDecisionType());
    }

    @Override public InputStream doFilterResponse(String requestId, String contentType, NameAndValue[] meta, InputStream in) {

        final String blockSpecJson = NameAndValue.find(meta, META_BLOCK_FILTERS);
        if (empty(blockSpecJson) || !isHtml(contentType)) return in;

        final String replacement = "<head><script>" + getBubbleJs(requestId, blockSpecJson) + "</script>";
        final RegexReplacementFilter filter = new RegexReplacementFilter("<head>", replacement);
        final RegexFilterReader reader = new RegexFilterReader(new InputStreamReader(in), filter).setMaxMatches(1);
        return new ReaderInputStream(reader, UTF8cs);
    }

    public static final Class<BubbleBlock> BB = BubbleBlock.class;
    public static final String BUBBLE_JS_TEMPLATE = stream2string(getPackagePath(BB)+"/"+ BB.getSimpleName()+".js.hbs");
    private static final String CTX_BUBBLE_BLOCK_SPEC_JSON = "BUBBLE_BLOCK_SPEC_JSON";

    private String getBubbleJs(String requestId, String blockSpecJson) {
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(CTX_JS_PREFIX, "__bubble_block_"+sha256_hex(requestId)+"_");
        ctx.put(CTX_BUBBLE_REQUEST_ID, requestId);
        ctx.put(CTX_BUBBLE_HOME, configuration.getPublicUriBase());
        ctx.put(CTX_BUBBLE_DATA_ID, getDataId(requestId));
        ctx.put(CTX_BUBBLE_BLOCK_SPEC_JSON, blockSpecJson);
        return HandlebarsUtil.apply(getHandlebars(), BUBBLE_JS_TEMPLATE, ctx);
    }

}
