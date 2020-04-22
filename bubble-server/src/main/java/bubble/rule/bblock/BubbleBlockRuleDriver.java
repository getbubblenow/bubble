/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.bblock;

import bubble.abp.*;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.device.Device;
import bubble.resources.stream.FilterHttpRequest;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.AppRuleDriver;
import bubble.rule.FilterMatchDecision;
import bubble.rule.analytics.TrafficAnalyticsRuleDriver;
import bubble.service.stream.AppRuleHarness;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.ReaderInputStream;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.http.URIUtil;
import org.cobbzilla.util.io.regex.RegexFilterReader;
import org.cobbzilla.util.io.regex.RegexReplacementFilter;
import org.cobbzilla.util.string.StringUtil;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpContentTypes.isHtml;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

@Slf4j
public class BubbleBlockRuleDriver extends TrafficAnalyticsRuleDriver {

    private BlockList blockList = new BlockList();

    private static Map<String, BlockListSource> blockListCache = new ConcurrentHashMap<>();

    @Override public <C> Class<C> getConfigClass() { return (Class<C>) BubbleBlockConfig.class; }

    @Override public void init(JsonNode config, JsonNode userConfig, AppRule rule, AppMatcher matcher, Account account, Device device) {
        super.init(config, userConfig, rule, matcher, account, device);
        refreshBlockLists();
    }

    public void refreshBlockLists() {
        final BubbleBlockConfig bubbleBlockConfig = getRuleConfig();
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

        final BubbleBlockConfig bubbleBlockConfig = getRuleConfig();
        final BlockDecision decision = getDecision(filter.getFqdn(), filter.getUri(), filter.getUserAgent());
        final BlockDecisionType decisionType = decision.getDecisionType();
        switch (decisionType) {
            case block:
                if (log.isInfoEnabled()) log.info(prefix+"decision is BLOCK");
                incrementCounters(account, device, app, site, fqdn);
                return FilterMatchDecision.abort_not_found;  // block this request

            case allow: default:
                if (filter.hasReferer()) {
                    final FilterMatchDecision refererDecision = checkRefererDecision(filter, account, device, app, site, prefix);
                    if (refererDecision != null) return refererDecision;
                }
                if (log.isInfoEnabled()) log.info(prefix+"decision is ALLOW");
                return FilterMatchDecision.no_match;

            case filter:
                if (filter.hasReferer()) {
                    final FilterMatchDecision refererDecision = checkRefererDecision(filter, account, device, app, site, prefix);
                    if (refererDecision != null) return refererDecision;
                }
                final List<BlockSpec> specs = decision.getSpecs();
                if (empty(specs)) {
                    log.warn(prefix+"decision was 'filter' but no specs were found, returning no_match");
                    return FilterMatchDecision.no_match;
                } else {
                    if (!bubbleBlockConfig.inPageBlocks()) {
                        for (BlockSpec spec : specs) {
                            if (spec.hasNoSelector()) {
                                log.info(prefix+"decision was FILTER but a URL block was triggered and inPageBlocks are disabled (returning abort_not_found)");
                                return FilterMatchDecision.abort_not_found;
                            }
                        }
                        log.info(prefix+"decision was FILTER but no URL-blocks and inPageBlocks are disabled (returning no_match)");
                        return FilterMatchDecision.no_match;
                    }
                    if (log.isInfoEnabled()) log.info(prefix+"decision is FILTER (returning match)");
                    return FilterMatchDecision.match;
                }
        }
    }

    public FilterMatchDecision checkRefererDecision(FilterMatchersRequest filter, Account account, Device device, String app, String site, String prefix) {
        prefix = prefix+" (checkRefererDecision): ";
        final URI refererURI = URIUtil.toUriOrNull(filter.getReferer());
        if (refererURI == null) {
            if (log.isInfoEnabled()) log.info(prefix+"invalid referer ("+filter.getReferer()+")");
            return null;
        } else {
            if (log.isInfoEnabled()) log.info(prefix+"checking referer: ("+filter.getReferer()+")");
        }
        final String refererHost = refererURI.getHost();
        final String refererPath = refererURI.getPath();
        final String userAgent = filter.getUserAgent();
        if (log.isInfoEnabled()) log.info(prefix+"decision for URL was ALLOW, checking against referer: host="+refererURI.getHost()+", path="+refererURI.getPath());
        final BlockDecision refererDecision = getDecision(refererHost, refererPath, userAgent);
        switch (refererDecision.getDecisionType()) {
            case block:
                if (log.isInfoEnabled()) log.info(prefix+"decision for URL was ALLOW but for referer is BLOCK");
                incrementCounters(account, device, app, site, refererHost);
                return FilterMatchDecision.abort_not_found;  // block this request
            case filter:
                if (log.isInfoEnabled()) log.info(prefix+"decision is FILTER (after checking referer), returning null");
                return null;
            case allow: default:
                if (log.isInfoEnabled()) log.info(prefix+"decision is ALLOW (after checking referer), returning null");
                return null;
        }
    }

    public BlockDecision getDecision(String fqdn, String uri, String userAgent) { return blockList.getDecision(fqdn, uri, userAgent, false); }

    public BlockDecision getDecision(String fqdn, String uri, String userAgent, boolean primary) {
        final BubbleBlockConfig bubbleBlockConfig = getRuleConfig();
        if (!empty(userAgent) && !empty(bubbleBlockConfig.getUserAgentBlocks())) {
            for (BubbleUserAgentBlock uaBlock : bubbleBlockConfig.getUserAgentBlocks()) {
                if (uaBlock.hasUrlRegex() && uaBlock.urlMatches(uri)) {
                    if (uaBlock.userAgentMatches(userAgent)) return BlockDecision.BLOCK;
                }
            }
        }
        return blockList.getDecision(fqdn, uri, primary);
    }

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

        final String replacement = "<head><script>" + getBubbleJs(filterRequest.getId(), decision) + "</script>";
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
        ctx.put(CTX_JS_PREFIX, AppRuleDriver.getJsPrefix(requestId));
        ctx.put(CTX_BUBBLE_REQUEST_ID, requestId);
        ctx.put(CTX_BUBBLE_HOME, configuration.getPublicUriBase());
        ctx.put(CTX_BUBBLE_DATA_ID, getDataId(requestId));
        ctx.put(CTX_BUBBLE_SELECTORS, json(decision.getSelectors(), COMPACT_MAPPER));
        ctx.put(CTX_BUBBLE_WHITELIST, json(blockList.getWhitelistDomains(), COMPACT_MAPPER));
        ctx.put(CTX_BUBBLE_BLACKLIST, json(blockList.getBlacklistDomains(), COMPACT_MAPPER));
        return HandlebarsUtil.apply(getHandlebars(), BUBBLE_JS_TEMPLATE, ctx);
    }

}
