package bubble.rule.bblock;

import bubble.abp.BlockDecision;
import bubble.abp.BlockList;
import bubble.abp.BlockListSource;
import bubble.abp.BlockSpec;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.device.Device;
import bubble.resources.stream.FilterMatchResponse;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.FilterMatchDecision;
import bubble.rule.analytics.TrafficAnalyticsRuleDriver;
import bubble.service.stream.AppRuleHarness;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.ReaderInputStream;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.http.HttpContentTypes;
import org.cobbzilla.util.io.regex.RegexFilterReader;
import org.cobbzilla.util.io.regex.RegexReplacementFilter;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

@Slf4j
public class BubbleBlockRuleDriver extends TrafficAnalyticsRuleDriver {

    private static final String META_REQUEST = "__bubble_request";

    private BubbleBlockConfig bubbleBlockConfig;

    private BlockList blockList = new BlockList();

    private static Map<String, BlockListSource> blockListCache = new ConcurrentHashMap<>();

    @Override public void init(JsonNode config, JsonNode userConfig, AppRule rule, AppMatcher matcher, Account account, Device device) {
        super.init(config, userConfig, rule, matcher, account, device);

        bubbleBlockConfig = json(json(config), BubbleBlockConfig.class);
        for (BubbleBlockList list : bubbleBlockConfig.getBlockLists()) {
            if (!list.enabled()) continue;

            BlockListSource blockListSource = blockListCache.get(list.getId());
            if (list.hasUrl()) {
                final String listUrl = list.getUrl();
                if (blockListSource == null || blockListSource.age() > TimeUnit.DAYS.toMillis(5)) {
                    try {
                        final BlockListSource newList = new BlockListSource()
                                .setUrl(listUrl)
                                .download();
                        blockListCache.put(list.getId(), newList);
                        blockListSource = newList;
                    } catch (Exception e) {
                        log.error("init: error downloading blocklist " + listUrl + ": " + shortError(e));
                        continue;
                    }
                }
            }
            if (list.hasAdditionalEntries()) {
                if (blockListSource == null) blockListSource = new BlockListSource(); // might be built-in source
                try {
                    blockListSource.addEntries(list.getAdditionalEntries());
                } catch (IOException e) {
                    log.error("init: error adding additional entries: "+shortError(e));
                }
            }
            if (blockListSource != null) blockList.merge(blockListSource.getBlockList());
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

        final BlockDecision decision = getDecision(filter.getFqdn(), filter.getUri());
        switch (decision.getDecisionType()) {
            case block:
                if (log.isDebugEnabled()) log.debug("preprocess: decision is BLOCK");
                incrementCounters(account, device, app, site, fqdn);
                return FilterMatchResponse.ABORT_NOT_FOUND;  // block this request
            case allow: default:
                if (log.isDebugEnabled()) log.debug("preprocess: decision is ALLOW");
                return FilterMatchResponse.NO_MATCH;
            case filter:
                if (log.isDebugEnabled()) log.debug("preprocess: decision is FILTER");
                return getFilterMatchResponse(filter, decision);
        }
    }

    public BlockDecision getDecision(String fqdn, String uri) { return blockList.getDecision(fqdn, uri, false); }

    public BlockDecision getDecision(String fqdn, String uri, boolean primary) { return blockList.getDecision(fqdn, uri, primary); }

    public FilterMatchResponse getFilterMatchResponse(FilterMatchersRequest filter, BlockDecision decision) {
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
                            .setMeta(new NameAndValue[]{
                                    new NameAndValue(META_REQUEST, json(filter, COMPACT_MAPPER))
                            });
                }
        }
        return die("getFilterMatchResponse: invalid decisionType: "+decision.getDecisionType());
    }

    @Override public InputStream doFilterResponse(String requestId, String contentType, NameAndValue[] meta, InputStream in) {

        final String requestJson = NameAndValue.find(meta, META_REQUEST);
        if (requestJson == null) {
            log.error("doFilterResponse: no request found, returning EMPTY_STREAM");
            return EMPTY_STREAM;
        }

        final FilterMatchersRequest request;
        try {
            request = json(requestJson, FilterMatchersRequest.class);
        } catch (Exception e) {
            log.error("doFilterResponse: error parsing request, returning EMPTY_STREAM: "+shortError(e));
            return EMPTY_STREAM;
        }

        // Now that we know the content type, re-check the BlockList
        final BlockDecision decision = blockList.getDecision(request.getFqdn(), request.getUri(), contentType, true);
        switch (decision.getDecisionType()) {
            case block:
                log.warn("doFilterRequest: preprocessed request was filtered, but ultimate decision was block (contentType="+contentType+"), returning EMPTY_STREAM");
                return EMPTY_STREAM;
            case allow:
                log.warn("doFilterRequest: preprocessed request was filtered, but ultimate decision was allow (contentType="+contentType+"), returning as-is");
                return in;
            case filter:
                if (!decision.hasSpecs()) {
                    // should never happen
                    log.warn("doFilterRequest: preprocessed request was filtered, but ultimate decision was filtered (contentType="+contentType+"), but no filters provided, returning as-is");
                    return in;
                }
                break;
            default:
                // should never happen
                log.warn("doFilterRequest: preprocessed request was filtered, but ultimate decision was invalid, returning EMPTY_STREAM");
                return EMPTY_STREAM;
        }

        if (!HttpContentTypes.isHtml(contentType)) {
            log.warn("doFilterRequest: cannot filter non-html response ("+request.getUrl()+"), returning as-is: "+contentType);
            return in;
        }

        final String replacement = "<head><script>" + getBubbleJs(requestId, decision) + "</script>";
        final RegexReplacementFilter filter = new RegexReplacementFilter("<head>", replacement);
        final RegexFilterReader reader = new RegexFilterReader(new InputStreamReader(in), filter).setMaxMatches(1);
        if (log.isDebugEnabled()) log.debug("doFilterResponse: filtering response for "+request.getUri()+" - replacement.length = "+replacement.length());
        return new ReaderInputStream(reader, UTF8cs);
    }

    public static final Class<BubbleBlockRuleDriver> BB = BubbleBlockRuleDriver.class;
    public static final String BUBBLE_JS_TEMPLATE = stream2string(getPackagePath(BB)+"/"+ BB.getSimpleName()+".js.hbs");
    private static final String CTX_BUBBLE_SELECTORS = "BUBBLE_SELECTORS_JSON";
    private static final String CTX_BUBBLE_BLACKLIST = "BUBBLE_BLACKLIST_JSON";
    private static final String CTX_BUBBLE_WHITELIST = "BUBBLE_WHITELIST_JSON";

    private String getBubbleJs(String requestId, BlockDecision decision) {
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(CTX_JS_PREFIX, "__bubble_block_"+sha256_hex(requestId)+"_");
        ctx.put(CTX_BUBBLE_REQUEST_ID, requestId);
        ctx.put(CTX_BUBBLE_HOME, configuration.getPublicUriBase());
        ctx.put(CTX_BUBBLE_DATA_ID, getDataId(requestId));
        ctx.put(CTX_BUBBLE_SELECTORS, json(decision.getSelectors(), COMPACT_MAPPER));
        ctx.put(CTX_BUBBLE_WHITELIST, json(blockList.getWhitelistDomains(), COMPACT_MAPPER));
        ctx.put(CTX_BUBBLE_BLACKLIST, json(blockList.getBlacklistDomains(), COMPACT_MAPPER));
        return HandlebarsUtil.apply(getHandlebars(), BUBBLE_JS_TEMPLATE, ctx);
    }

}
