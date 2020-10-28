/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.bblock;

import bubble.abp.*;
import bubble.dao.app.AppDataDAO;
import bubble.model.account.Account;
import bubble.model.app.*;
import bubble.model.device.BlockStatsDisplayMode;
import bubble.model.device.Device;
import bubble.resources.stream.FilterHttpRequest;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.FilterMatchDecision;
import bubble.rule.RequestModifierConfig;
import bubble.rule.RequestModifierRule;
import bubble.rule.analytics.TrafficAnalyticsRuleDriver;
import bubble.server.BubbleConfiguration;
import bubble.service.device.DeviceService;
import bubble.service.stream.AppRuleHarness;
import bubble.service.stream.ConnectionCheckResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.SingletonMap;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.http.URIUtil;
import org.cobbzilla.util.string.StringUtil;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static bubble.service.stream.HttpStreamDebug.getLogFqdn;
import static java.util.concurrent.TimeUnit.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.EMPTY;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;
import static org.cobbzilla.util.string.ValidationRegexes.HOST_PATTERN;
import static org.cobbzilla.util.string.ValidationRegexes.validateRegexMatches;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public class BubbleBlockRuleDriver extends TrafficAnalyticsRuleDriver
        implements RequestModifierRule, HasAppDataCallback {

    public static final String DEFAULT_SITE_NAME = "All_Sites";
    public static final String PREFIX_APPDATA_SHOW_STATS = "showStats_";

    private final AtomicReference<BlockList> blockList = new AtomicReference<>(new BlockList());
    private BlockList getBlockList() { return blockList.get(); }

    private final AtomicReference<Set<String>> fullyBlockedDomains = new AtomicReference<>(Collections.emptySet());
    @Override public Set<String> getPrimedBlockDomains() { return fullyBlockedDomains.get(); }

    private final AtomicReference<Set<String>> whiteListDomains = new AtomicReference<>(Collections.emptySet());
    @Override public Set<String> getPrimedWhiteListDomains() { return whiteListDomains.get(); }

    private final AtomicReference<Set<String>> rejectDomains = new AtomicReference<>(Collections.emptySet());
    @Override public Set<String> getPrimedRejectDomains() { return rejectDomains.get(); }

    private final AtomicReference<Set<String>> partiallyBlockedDomains = new AtomicReference<>(Collections.emptySet());
    @Override public Set<String> getPrimedFilterDomains() { return partiallyBlockedDomains.get(); }

    private final static Map<String, BlockListSource> blockListCache = new ConcurrentHashMap<>();

    public boolean showStats(Device device, String ip, String fqdn) {
        if (!device.getSecurityLevel().statsEnabled()) return false;
        if (!deviceService.doShowBlockStats(account.getUuid())) return false;
        final Boolean show = deviceService.doShowBlockStatsForIpAndFqdn(ip, fqdn);
        return show != null ? show : device.getSecurityLevel().preferStatsOn();
    }

    @Override public <C> Class<C> getConfigClass() { return (Class<C>) BubbleBlockConfig.class; }

    @Override public RequestModifierConfig getRequestModifierConfig() { return getRuleConfig(); }

    @Override public boolean couldModify(FilterHttpRequest request) {
        final BubbleBlockConfig config = getRuleConfig();
        final FilterMatchersRequest req = request.getMatchersResponse().getRequest();
        return request.isHtml() && request.isBrowser() && (config.inPageBlocks() || showStats(request.getDevice(), req.getClientAddr(), req.getFqdn()));
    }

    @Override public void init(JsonNode config,
                               JsonNode userConfig,
                               BubbleApp app,
                               AppRule rule,
                               AppMatcher matcher,
                               Account account,
                               Device device) {
        initQuick(config, userConfig, app, rule, matcher, account, device);
        refreshLists();
    }

    @Override public void initQuick(JsonNode config,
                                    JsonNode userConfig,
                                    BubbleApp app,
                                    AppRule rule,
                                    AppMatcher matcher,
                                    Account account,
                                    Device device) {
        super.init(config, userConfig, app, rule, matcher, account, device);
    }

    @Override public JsonNode upgradeRuleConfig(JsonNode sageRuleConfig,
                                                JsonNode localRuleConfig) {
        final BubbleBlockConfig sageConfig = json(sageRuleConfig, getConfigClass());
        final BubbleBlockConfig localConfig = json(localRuleConfig, getConfigClass());
        if (sageConfig.hasBlockLists()) {
            for (BubbleBlockList sageList : sageConfig.getBlockLists()) {
                if (!localConfig.hasBlockLists() || !localConfig.hasBlockList(sageList)) {
                    localConfig.addBlockList(sageList);
                }
            }
        }
        return json(json(localConfig), JsonNode.class);
    }

    private final Map<Account, AppSite> defaultSites = new ExpirationMap<>(4, HOURS.toMillis(1));
    private AppSite getDefaultSite (Account account, BubbleApp app) {
        return defaultSites.computeIfAbsent(account, a -> appSiteDAO.findByAccountAndAppAndId(account.getUuid(), app.getUuid(), DEFAULT_SITE_NAME));
    }

    private static final Map<String, List<String>> statsDisplayLists = new ExpirationMap<>(2, MINUTES.toMillis(5));
    private static List<String> getUrlLines (BubbleBlockStatsDisplayList list) {
        return statsDisplayLists.computeIfAbsent(list.getUrl(), k -> list.loadLines());
    }

    public void refreshLists() {
        log.info("refreshLists: starting");
        final BubbleBlockConfig bubbleBlockConfig = getRuleConfig();
        refreshBlockDisplayLists(bubbleBlockConfig);
        if (bubbleBlockConfig.hasBlockLists()) {
            final BubbleBlockList[] blockLists = bubbleBlockConfig.getBlockLists();
            final Set<String> refreshed = new HashSet<>();
            final BlockList newBlockList = new BlockList();
            for (BubbleBlockList list : blockLists) {
                if (!list.enabled()) continue;

                BlockListSource blockListSource = blockListCache.get(list.getId());
                if (list.hasUrl()) {
                    final String listUrl = list.getUrl();
                    if (blockListSource == null || blockListSource.age() > DAYS.toMillis(5)) {
                        try {
                            final BlockListSource newList = new BlockListSource()
                                    .setUrl(listUrl)
                                    .download();
                            blockListCache.put(list.getId(), newList);
                            blockListSource = newList;
                            refreshed.add(newList.getUrl());
                        } catch (Exception e) {
                            log.error("init: error downloading blockList " + listUrl + ": " + shortError(e));
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
                        log.error("init: error adding additional entries: " + shortError(e));
                    }
                }
                if (blockListSource != null) newBlockList.merge(blockListSource.getBlockList());
            }
            blockList.set(newBlockList);

            if (!newBlockList.getRejectList().equals(rejectDomains.get())) {
                rejectDomains.set(newBlockList.getRejectList());
            }
            if (!newBlockList.getFullyBlockedDomains().equals(fullyBlockedDomains.get())) {
                fullyBlockedDomains.set(newBlockList.getFullyBlockedDomains());
            }
            if (!newBlockList.getPartiallyBlockedDomains().equals(partiallyBlockedDomains.get())) {
                partiallyBlockedDomains.set(newBlockList.getPartiallyBlockedDomains());
            }
            if (!newBlockList.getWhitelistDomainNames().equals(whiteListDomains.get())) {
                whiteListDomains.set(newBlockList.getWhitelistDomainNames());
            }

            log.debug("refreshBlockLists: rejectDomains=" + rejectDomains.get().size());
            log.debug("refreshBlockLists: fullyBlockedDomains=" + fullyBlockedDomains.get().size());
            log.debug("refreshBlockLists: partiallyBlockedDomains=" + partiallyBlockedDomains.get().size());
            log.debug("refreshBlockLists: refreshed " + refreshed.size() + " block lists: " + StringUtil.toString(refreshed));
        }
    }

    protected void refreshBlockDisplayLists(BubbleBlockConfig bubbleBlockConfig) {
        log.info("refreshBlockDisplayLists: starting");
        if (bubbleBlockConfig.hasStatsDisplayLists()) {
            final BubbleBlockStatsDisplayList[] displayLists = bubbleBlockConfig.getStatsDisplayLists();
            log.info("refreshBlockDisplayLists: starting with "+displayLists.length+" lists...");
            for (BubbleBlockStatsDisplayList list : displayLists) {
                try {
                    final List<String> lines = getUrlLines(list);
                    if (log.isInfoEnabled()) log.info("refreshBlockDisplayLists: loaded "+lines.size()+" domains from list "+list.getId()+": "+list.getUrl());
                    for (String domain : lines) {
                        final String key = PREFIX_APPDATA_SHOW_STATS + domain;
                        final List<AppData> data = appDataDAO.findByAccountAndAppAndKey(account.getUuid(), app.getUuid(), key);
                        if (empty(data)) {
                            final String value = String.valueOf(list.getMode() == BlockStatsDisplayMode.default_on);
                            if (log.isInfoEnabled()) log.info("refreshBlockDisplayLists: creating AppData("+key+") => "+value);
                            appDataDAO.create(new AppData()
                                    .setAccount(account.getUuid())
                                    .setApp(app.getUuid())
                                    .setMatcher(matcher.getUuid())
                                    .setSite(getDefaultSite(account, app).getUuid())
                                    .setKey(key)
                                    .setData(value)
                            );
                        } else {
                            if (log.isDebugEnabled()) log.debug("refreshBlockDisplayLists: AppData("+key+") already exists, found records: "+json(data));
                        }
                    }
                } catch (Exception e) {
                    log.warn("refreshBlockDisplayLists: error loading statsDisplayList ("+list.getUrl()+"): "+shortError(e), e);
                }
            }
        }
    }

    @Override public ConnectionCheckResponse checkConnection(AppRuleHarness harness,
                                                             Account account,
                                                             Device device,
                                                             String clientAddr,
                                                             String serverAddr,
                                                             String fqdn) {
        final BlockDecision decision = getBlockList().getFqdnDecision(fqdn);
        final BlockDecisionType decisionType = decision.getDecisionType();
        switch (decisionType) {
            case allow:
                return showStats(device, clientAddr, fqdn) ? ConnectionCheckResponse.filter : ConnectionCheckResponse.noop;
            case block:
                return ConnectionCheckResponse.block;
            default:
                if (log.isWarnEnabled()) log.warn("checkConnection: unexpected decision: "+decisionType+", returning noop");
                return ConnectionCheckResponse.noop;
        }
    }

    @Override public FilterMatchDecision preprocess(AppRuleHarness ruleHarness,
                                                    FilterMatchersRequest filter,
                                                    Account account,
                                                    Device device,
                                                    Request req,
                                                    ContainerRequest request) {
        final boolean extraLog = filter.getFqdn().contains(getLogFqdn());
        final String app = ruleHarness.getRule().getApp();
        final String site = ruleHarness.getMatcher().getSite();
        final String fqdn = filter.getFqdn();
        final String prefix = "preprocess("+filter.getRequestId()+"): ";

        final BubbleBlockConfig bubbleBlockConfig = getRuleConfig();
        final BlockDecision decision = getPreprocessDecision(filter.getFqdn(), filter.getUri(), filter.getUserAgent(), filter.getReferer());
        final BlockDecisionType decisionType = decision.getDecisionType();
        final FilterMatchDecision subDecision;
        switch (decisionType) {
            case block:
                if (log.isInfoEnabled()) log.info(prefix+"decision is BLOCK");
                else if (extraLog) log.error(prefix+"decision is BLOCK");
                incrementCounters(account, device, app, site, fqdn);
                return FilterMatchDecision.abort_not_found;  // block this request

            case allow: default:
                subDecision = checkRefererAndShowStats(decisionType, filter, account, device, extraLog, app, site, prefix);
                if (subDecision != null) return subDecision;
                if (log.isInfoEnabled()) log.info(prefix+"decision is ALLOW");
                else if (extraLog) log.error(prefix+"decision is ALLOW");
                return FilterMatchDecision.no_match;

            case filter:
                subDecision = checkRefererAndShowStats(decisionType, filter, account, device, extraLog, app, site, prefix);
                if (subDecision != null) return subDecision;
                final List<BlockSpec> specs = decision.getSpecs();
                if (empty(specs)) {
                    if (log.isWarnEnabled()) log.warn(prefix+"decision was 'filter' but no specs were found, returning no_match");
                    else if (extraLog) log.error(prefix+"decision was 'filter' but no specs were found, returning no_match");
                    return FilterMatchDecision.no_match;
                } else {
                    if (!bubbleBlockConfig.inPageBlocks()) {
                        for (BlockSpec spec : specs) {
                            if (spec.hasNoSelector()) {
                                if (log.isInfoEnabled()) log.info(prefix+"decision was FILTER but a URL block was triggered and inPageBlocks are disabled (returning abort_not_found)");
                                else if (extraLog) log.error(prefix+"decision was FILTER but a URL block was triggered and inPageBlocks are disabled (returning abort_not_found)");
                                return FilterMatchDecision.abort_not_found;
                            }
                        }
                        if (log.isInfoEnabled()) log.info(prefix+"decision was FILTER but no URL-blocks and both inPageBlocks and showStats are disabled (returning no_match)");
                        else if (extraLog) log.error(prefix+"decision was FILTER but no URL-blocks and both inPageBlocks and showStats are disabled (returning no_match)");
                        return FilterMatchDecision.no_match;
                    }
                    if (log.isInfoEnabled()) log.info(prefix+"decision is FILTER (returning match)");
                    else if (extraLog) log.error(prefix+"decision is FILTER (returning match)");
                    return FilterMatchDecision.match;
                }
        }
    }

    public FilterMatchDecision checkRefererAndShowStats(BlockDecisionType decisionType,
                                                        FilterMatchersRequest filter,
                                                        Account account,
                                                        Device device,
                                                        boolean extraLog,
                                                        String app,
                                                        String site,
                                                        String prefix) {
        prefix += "(checkRefererAndShowStats): ";
        if (filter.hasReferer()) {
            final FilterMatchDecision refererDecision = checkRefererDecision(filter, account, device, app, site, prefix);
            if (refererDecision != null && refererDecision.isAbort()) {
                if (log.isInfoEnabled()) log.info(prefix+"decision was "+decisionType+" but refererDecision was "+refererDecision+", returning "+refererDecision);
                else if (extraLog) log.error(prefix+"decision was "+decisionType+" but refererDecision was "+refererDecision+", returning "+refererDecision);
                return refererDecision;
            }
        }
        if (showStats(device, filter.getClientAddr(), filter.getFqdn())) {
            if (log.isInfoEnabled()) log.info(prefix+"decision was "+decisionType+" but showStats=true, returning match");
            else if (extraLog) log.error(prefix+"decision was "+decisionType+" but showStats=true, returning match");
            return FilterMatchDecision.match;
        } else {
            if (log.isInfoEnabled()) log.info(prefix+"decision was "+decisionType+" but showStats=false, returning null");
            else if (extraLog) log.error(prefix+"decision was "+decisionType+" but showStats=false, returning null");
        }
        return null;
    }

    public FilterMatchDecision checkRefererDecision(FilterMatchersRequest filter, Account account, Device device, String app, String site, String prefix) {
        prefix = prefix+" (checkRefererDecision): ";
        final String referer = filter.referrerUrlOnly();
        final URI refererURI = URIUtil.toUriOrNull(referer);
        if (refererURI == null) {
            if (log.isInfoEnabled()) log.info(prefix+"invalid referer ("+ referer +")");
            return null;
        } else {
            if (log.isInfoEnabled()) log.info(prefix+"checking referer: ("+ referer +")");
        }
        final String refererHost = refererURI.getHost();
        final String refererPath = refererURI.getPath();
        final String userAgent = filter.getUserAgent();
        if (log.isInfoEnabled()) log.info(prefix+"decision for URL was ALLOW, checking against referer: host="+refererURI.getHost()+", path="+refererURI.getPath());
        final BlockDecision refererDecision = getPreprocessDecision(refererHost, refererPath, userAgent, refererHost);
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

    public BlockDecision getPreprocessDecision(String fqdn, String uri, String userAgent, String referer) {
        if (isBlockedUserAgent(uri, userAgent)) return BlockDecision.BLOCK;
        return getBlockList().getDecision(fqdn, uri, null, referer, false);
    }

    public BlockDecision getDecision(String fqdn, String uri, String userAgent, boolean primary) {
        if (isBlockedUserAgent(uri, userAgent)) return BlockDecision.BLOCK;
        return getBlockList().getDecision(fqdn, uri, primary);
    }

    private boolean isBlockedUserAgent(String uri, String userAgent) {
        if (!empty(userAgent)) {
            final BubbleBlockConfig bubbleBlockConfig = getRuleConfig();
            if (!empty(bubbleBlockConfig.getUserAgentBlocks())) {
                for (BubbleUserAgentBlock uaBlock : bubbleBlockConfig.getUserAgentBlocks()) {
                    if (uaBlock.hasUrlRegex() && uaBlock.urlMatches(uri)) {
                        if (uaBlock.userAgentMatches(userAgent)) return true;
                    }
                }
            }
        }
        return false;
    }

    public static final String FILTER_CTX_DECISION = "decision";
    public static final String BLOCK_STATS_JS = "BLOCK_STATS_JS";

    @Override public InputStream doFilterResponse(FilterHttpRequest filterRequest, InputStream in, Charset charset) {

        final FilterMatchersRequest request = filterRequest.getMatchersResponse().getRequest();
        final String prefix = "doFilterResponse("+filterRequest.getId()+"): ";
        final BubbleBlockConfig bubbleBlockConfig = getRuleConfig();

        // todo: add support for stream blockers: we may allow the request but wrap the returned InputStream
        // if the wrapper detects it should be blocked, then the connection cut short
        // for example: if the content-type is image/* we can read the image header, if the dimensions of the image
        // are a standard IAB ad unit, kill the connection, or replace the image with our data

        // Now that we know the content type, re-check the BlockList
        final String contentType = filterRequest.getContentType();
        final BlockDecision decision = getBlockList().getDecision(request.getFqdn(), request.getUri(), contentType, request.getReferer(), true);
        final Map<String, Object> filterCtx = new SingletonMap<>(FILTER_CTX_DECISION, decision);
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

        if (!filterRequest.isHtml()) {
            log.warn(prefix+"cannot request non-html response ("+request.getUrl()+"), returning as-is: "+contentType);
            if (log.isInfoEnabled()) log.info(prefix+"SEND: unfiltered response (non-html content-type) for "+request.getUrl());
            return in;
        }

        final boolean showStats = showStats(filterRequest.getDevice(), request.getClientAddr(), request.getFqdn());
        if (!bubbleBlockConfig.inPageBlocks() && !showStats) {
            if (log.isInfoEnabled()) log.info(prefix + "SEND: both inPageBlocks and showStats are false, returning as-is");
            return in;
        }
        if (bubbleBlockConfig.inPageBlocks() && showStats) {
            if (log.isInfoEnabled()) log.info(prefix + "SEND: both inPageBlocks and showStats are true, filtering");
            return filterInsertJs(in, charset, filterRequest, filterCtx, BUBBLE_JS_TEMPLATE, getBubbleJsStatsTemplate(), BLOCK_STATS_JS, showStats);
        }
        if (bubbleBlockConfig.inPageBlocks()) {
            if (log.isInfoEnabled()) log.info(prefix + "SEND: both inPageBlocks is true, filtering");
            return filterInsertJs(in, charset, filterRequest, filterCtx, BUBBLE_JS_TEMPLATE, EMPTY, BLOCK_STATS_JS, showStats);
        }
        if (log.isInfoEnabled()) log.info(prefix+"inserting JS for stats into: "+request.getUrl()+" with Content-Type: "+filterRequest.getContentType());
        return filterInsertJs(in, charset, filterRequest, filterCtx, getBubbleJsStatsTemplate(), null, null, showStats);
    }

    protected String getBubbleJsStatsTemplate () {
        return loadTemplate(BUBBLE_JS_STATS_TEMPLATE, BUBBLE_STATS_TEMPLATE_NAME);
    }

    public static final Class<BubbleBlockRuleDriver> BB = BubbleBlockRuleDriver.class;
    public static final String BUBBLE_JS_TEMPLATE = stream2string(getPackagePath(BB)+"/"+ BB.getSimpleName()+".js.hbs");

    public static final String BUBBLE_STATS_TEMPLATE_NAME = BB.getSimpleName() + "_stats.js.hbs";
    public static final String BUBBLE_JS_STATS_TEMPLATE = stream2string(getPackagePath(BB) + "/" + BUBBLE_STATS_TEMPLATE_NAME);

    private static final String CTX_BUBBLE_SELECTORS = "BUBBLE_SELECTORS_JSON";
    private static final String CTX_BUBBLE_BLACKLIST = "BUBBLE_BLACKLIST_JSON";
    private static final String CTX_BUBBLE_WHITELIST = "BUBBLE_WHITELIST_JSON";

    @Override protected Map<String, Object> getBubbleJsContext(FilterHttpRequest filterRequest, Map<String, Object> filterCtx) {
        final Map<String, Object> ctx = super.getBubbleJsContext(filterRequest, filterCtx);
        final BubbleBlockConfig bubbleBlockConfig = getRuleConfig();
        if (bubbleBlockConfig.inPageBlocks()) {
            final BlockDecision decision = (BlockDecision) filterCtx.get(FILTER_CTX_DECISION);
            ctx.put(CTX_BUBBLE_SELECTORS, json(decision.getSelectors(), COMPACT_MAPPER));
            ctx.put(CTX_BUBBLE_WHITELIST, json(getBlockList().getWhitelistDomains(), COMPACT_MAPPER));
            ctx.put(CTX_BUBBLE_BLACKLIST, json(getBlockList().getBlacklistDomains(), COMPACT_MAPPER));
        }
        return ctx;
    }

    public static String fqdnFromKey(String key) {
        if (!key.startsWith(PREFIX_APPDATA_SHOW_STATS)) die("expected key to start with '"+ PREFIX_APPDATA_SHOW_STATS +"', was: "+ key);
        return key.substring(PREFIX_APPDATA_SHOW_STATS.length());
    }

    @Override public void prime(Account account, BubbleApp app, BubbleConfiguration configuration) {
        final DeviceService deviceService = configuration.getBean(DeviceService.class);
        final AppDataDAO dataDAO = configuration.getBean(AppDataDAO.class);
        log.info("priming app="+app.getName());
        dataDAO.findByAccountAndAppAndAndKeyPrefix(account.getUuid(), app.getUuid(), PREFIX_APPDATA_SHOW_STATS)
                .forEach(data -> deviceService.setBlockStatsForFqdn(account, fqdnFromKey(data.getKey()), Boolean.parseBoolean(data.getData())));
    }

    @Override public Function<AppData, AppData> createCallback(Account account,
                                                               BubbleApp app,
                                                               BubbleConfiguration configuration) {
        return data -> {
            final String prefix = "createCallback("+data.getKey()+"="+data.getData()+"): ";
            if (log.isDebugEnabled()) log.debug(prefix+"starting with data="+json(data));
            if (data.getKey().startsWith(PREFIX_APPDATA_SHOW_STATS)) {
                final DeviceService deviceService = configuration.getBean(DeviceService.class);
                final String fqdn = fqdnFromKey(data.getKey());
                if (validateRegexMatches(HOST_PATTERN, fqdn)) {
                    if (data.deleting()) {
                        if (log.isInfoEnabled()) log.info(prefix+"unsetting fqdn: "+fqdn);
                        deviceService.unsetBlockStatsForFqdn(account, fqdn);
                    } else {
                        if (log.isInfoEnabled()) log.info(prefix+"setting fqdn: "+fqdn);
                        deviceService.setBlockStatsForFqdn(account, fqdn, Boolean.parseBoolean(data.getData()));
                    }
                } else {
                    throw invalidEx("err.fqdn.invalid", "not a valid FQDN: "+fqdn, fqdn);
                }
                data.setDevice(null); // block stats are enabled/disabled for all devices
            }
            return data;
        };
    }

}
