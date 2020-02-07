package bubble.rule.bblock;

import bubble.abp.BlockDecision;
import bubble.abp.BlockList;
import bubble.abp.BlockListSource;
import bubble.abp.BlockSpec;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.device.Device;
import bubble.resources.stream.FilterHttpRequest;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.FilterMatchDecision;
import bubble.rule.analytics.TrafficAnalyticsRuleDriver;
import bubble.service.stream.AppRuleHarness;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.ReaderInputStream;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.regex.RegexFilterReader;
import org.cobbzilla.util.io.regex.RegexReplacementFilter;
import org.cobbzilla.util.string.StringUtil;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpContentTypes.isHtml;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

@Slf4j
public class BubbleBlockRuleDriver extends TrafficAnalyticsRuleDriver {

    private BubbleBlockConfig bubbleBlockConfig;

    private BlockList blockList = new BlockList();

    private static Map<String, BlockListSource> blockListCache = new ConcurrentHashMap<>();

    @Override public void init(JsonNode config, JsonNode userConfig, AppRule rule, AppMatcher matcher, Account account, Device device) {
        super.init(config, userConfig, rule, matcher, account, device);
        bubbleBlockConfig = json(json(config), BubbleBlockConfig.class);
        refreshBlockLists();
    }

    public void refreshBlockLists() {
        final BubbleBlockList[] blockLists = bubbleBlockConfig.getBlockLists();
        final Set<String> refreshed = new HashSet<>();
        for (BubbleBlockList list : blockLists) {
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
                        refreshed.add(newList.getUrl());
                    } catch (Exception e) {
                        log.error("init: error downloading blocklist " + listUrl + ": " + shortError(e));
                        continue;
                    }
                }
            } else {
                refreshed.add(list.getName());
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
        log.info("refreshBlockLists: refreshed "+refreshed.size()+" block lists: "+StringUtil.toString(refreshed));
    }

    @Override public FilterMatchDecision preprocess(AppRuleHarness ruleHarness,
                                                    FilterMatchersRequest filter,
                                                    Account account,
                                                    Device device,
                                                    Request req,
                                                    ContainerRequest request) {
        final String app = ruleHarness.getRule().getApp();
        final String site = ruleHarness.getMatcher().getSite();
        final String fqdn = filter.getFqdn();
        final String prefix = "preprocess("+filter.getRequestId()+"): ";

        final BlockDecision decision = getDecision(filter.getFqdn(), filter.getUri());
        switch (decision.getDecisionType()) {
            case block:
                if (log.isInfoEnabled()) log.info(prefix+"decision is BLOCK");
                incrementCounters(account, device, app, site, fqdn);
                return FilterMatchDecision.abort_not_found;  // block this request

            case allow: default:
                if (log.isInfoEnabled()) log.info(prefix+"decision is ALLOW");
                return FilterMatchDecision.no_match;

            case filter:
                if (log.isInfoEnabled()) log.info(prefix+"decision is FILTER");
                final List<BlockSpec> specs = decision.getSpecs();
                if (empty(specs)) {
                    log.warn(prefix+"decision was 'filter' but no specs were found, returning no_match");
                    return FilterMatchDecision.no_match;
                } else {
                    return FilterMatchDecision.match;
                }
        }
    }

    public BlockDecision getDecision(String fqdn, String uri) { return blockList.getDecision(fqdn, uri, false); }

    public BlockDecision getDecision(String fqdn, String uri, boolean primary) { return blockList.getDecision(fqdn, uri, primary); }

    @Override public InputStream doFilterResponse(FilterHttpRequest filterRequest, InputStream in) {

        final FilterMatchersRequest request = filterRequest.getMatchersResponse().getRequest();
        final String prefix = "doFilterResponse("+filterRequest.getId()+"): ";

        // Now that we know the content type, re-check the BlockList
        final String contentType = filterRequest.getContentType();
        final BlockDecision decision = blockList.getDecision(request.getFqdn(), request.getUri(), contentType, true);
        if (log.isDebugEnabled()) log.debug(prefix+"preprocess decision was "+decision+", but now we know contentType="+contentType);
        switch (decision.getDecisionType()) {
            case block:
                log.warn(prefix+"preprocessed request was filtered, but ultimate decision was block (contentType="+contentType+"), returning EMPTY_STREAM");
                if (log.isInfoEnabled()) log.info(prefix+"SEND: empty response (decision: block) for "+request.getUrl());
                return EMPTY_STREAM;
            case allow:
                log.warn(prefix+"preprocessed request was filtered, but ultimate decision was allow (contentType="+contentType+"), returning as-is");
                if (log.isInfoEnabled()) log.info(prefix+"SEND: unfiltered response (decision: allow) for "+request.getUrl());
                return in;
            case filter:
                if (!decision.hasSpecs()) {
                    // should never happen
                    log.warn(prefix+"preprocessed request was filtered, but ultimate decision was filtered (contentType="+contentType+"), but no filters provided, returning as-is");
                    if (log.isInfoEnabled()) log.info(prefix+"SEND: unfiltered response (decision: filter, but no filters) for "+request.getUrl());
                    return in;
                }
                break;
            default:
                // should never happen
                log.warn(prefix+"preprocessed request was filtered, but ultimate decision was invalid, returning EMPTY_STREAM");
                if (log.isInfoEnabled()) log.info(prefix+"SEND: unfiltered response (decision: invalid) for "+request.getUrl());
                return EMPTY_STREAM;
        }

        if (!isHtml(contentType)) {
            log.warn(prefix+"cannot request non-html response ("+request.getUrl()+"), returning as-is: "+contentType);
            if (log.isInfoEnabled()) log.info(prefix+"SEND: unfiltered response (non-html content-type) for "+request.getUrl());
            return in;
        }

        final String replacement = "<head><meta charset=\"UTF-8\"><script>" + getBubbleJs(filterRequest.getId(), decision) + "</script>";
        final RegexReplacementFilter filter = new RegexReplacementFilter("<head>", replacement);
        final RegexFilterReader reader = new RegexFilterReader(new InputStreamReader(in, UTF8cs), filter).setMaxMatches(1);
        if (log.isDebugEnabled()) {
            log.debug(prefix+"filtering response for "+request.getUrl()+" - replacement.length = "+replacement.length());
        } else if (log.isInfoEnabled()) {
            log.info(prefix+"SEND: filtering response for "+request.getUrl());
        }
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
