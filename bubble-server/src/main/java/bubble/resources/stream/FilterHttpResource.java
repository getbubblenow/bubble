/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.stream;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.AppMatcherDAO;
import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.AppSiteDAO;
import bubble.dao.app.BubbleAppDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.AppSite;
import bubble.model.app.BubbleApp;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.device.Device;
import bubble.model.device.DeviceSecurityLevel;
import bubble.model.device.DeviceStatus;
import bubble.rule.FilterMatchDecision;
import bubble.server.BubbleConfiguration;
import bubble.service.block.BlockStatsService;
import bubble.service.block.BlockStatsSummary;
import bubble.service.boot.SelfNodeService;
import bubble.service.device.DeviceService;
import bubble.service.device.FlexRouterInfo;
import bubble.service.device.StandardFlexRouterService;
import bubble.service.message.MessageService;
import bubble.service.stream.ConnectionCheckResponse;
import bubble.service.stream.StandardRuleEngineService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.cobbzilla.util.collection.ExpirationEvictionPolicy;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.http.HttpContentEncodingType;
import org.cobbzilla.util.network.NetworkUtil;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static bubble.ApiConstants.*;
import static bubble.resources.stream.FilterMatchersResponse.NO_MATCHERS;
import static bubble.rule.AppRuleDriver.isFlexRouteFqdn;
import static bubble.service.device.FlexRouterInfo.missingFlexRouter;
import static bubble.service.stream.HttpStreamDebug.getLogFqdn;
import static bubble.service.stream.StandardRuleEngineService.MATCHERS_CACHE_TIMEOUT;
import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.*;
import static org.apache.http.HttpHeaders.*;
import static org.cobbzilla.util.collection.ArrayUtil.arrayToString;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpContentTypes.TEXT_PLAIN;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.util.http.HttpUtil.applyRegexToUrl;
import static org.cobbzilla.util.http.HttpUtil.chaseRedirects;
import static org.cobbzilla.util.json.JsonUtil.*;
import static org.cobbzilla.util.network.NetworkUtil.isLocalIpv4;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.string.StringUtil.trimQuotes;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.model.NamedEntity.names;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.API_TAG_UTILITY;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Path(FILTER_HTTP_ENDPOINT)
@Service @Slf4j
public class FilterHttpResource {

    @Autowired private AccountDAO accountDAO;
    @Autowired private StandardRuleEngineService ruleEngine;
    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private BubbleAppDAO appDAO;
    @Autowired private AppSiteDAO siteDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private DeviceDAO deviceDAO;
    @Autowired private DeviceService deviceService;
    @Autowired private RedisService redis;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private SelfNodeService selfNodeService;
    @Autowired private BlockStatsService blockStats;
    @Autowired private StandardFlexRouterService flexRouterService;
    @Autowired private MessageService messageService;

    private static final long ACTIVE_REQUEST_TIMEOUT = HOURS.toSeconds(12);

    @Getter(lazy=true) private final RedisService activeRequestCache = redis.prefixNamespace(getClass().getSimpleName()+".requests");

    public FilterHttpRequest getActiveRequest(String requestId) {
        final String json = getActiveRequestCache().get(requestId);
        if (json == null) return null;
        return json(json, FilterHttpRequest.class);
    }

    private final Map<String, Account> accountCache = new ExpirationMap<>(MINUTES.toMillis(10));
    public Account findCaller(String accountUuid) {
        return accountCache.computeIfAbsent(accountUuid, uuid -> accountDAO.findByUuid(uuid));
    }

    private final Map<String, Device> deviceCache = new ExpirationMap<>(MINUTES.toMillis(10));
    public Device findDevice(String deviceUuid) {
        return deviceCache.computeIfAbsent(deviceUuid, uuid -> deviceDAO.findByUuid(uuid));
    }

    private FilterMatchersResponse getMatchersResponse(FilterMatchersRequest filterRequest,
                                                       Request req,
                                                       ContainerRequest request) {
        final RedisService cache = ruleEngine.getMatchersCache();

        final String requestId = filterRequest.getRequestId();
        final boolean extraLog = filterRequest.getFqdn().contains(getLogFqdn());
        final String prefix = "getMatchersResponse("+requestId+"): ";
        final String cacheKey = filterRequest.cacheKey();
        final String matchersJson = cache.get(cacheKey);
        if (matchersJson != null) {
            final FilterMatchersResponse cached = json(matchersJson, FilterMatchersResponse.class);
            cache.set(requestId, json(cached, COMPACT_MAPPER), EX, MATCHERS_CACHE_TIMEOUT);
            if (log.isTraceEnabled()) log.trace(prefix+"found cached response for cacheKey="+cacheKey+" and set for requestId "+requestId+": "+json(cached, COMPACT_MAPPER));
            else if (extraLog) log.error(prefix+"found cached response for cacheKey="+cacheKey+" and set for requestId "+requestId+": "+json(cached, COMPACT_MAPPER));
            return cached.setRequestId(requestId);
        }

        final FilterMatchersResponse response = findMatchers(filterRequest, req, request);
        if (log.isTraceEnabled()) log.trace(prefix+"writing cache-miss to redis under keys "+cacheKey+" and "+requestId+": "+json(response, COMPACT_MAPPER));
        else if (extraLog) log.error(prefix+"writing cache-miss to redis under keys "+cacheKey+" and "+requestId+": "+json(response, COMPACT_MAPPER));
        cache.set(cacheKey, json(response, COMPACT_MAPPER), EX, MATCHERS_CACHE_TIMEOUT);
        cache.set(requestId, json(response, COMPACT_MAPPER), EX, MATCHERS_CACHE_TIMEOUT);
        return response;
    }

    private FilterMatchersResponse getMatchersResponseByRequestId(String requestId) {
        final RedisService cache = ruleEngine.getMatchersCache();
        final String matchersJson = cache.get(requestId);
        if (matchersJson != null) return json(matchersJson, FilterMatchersResponse.class);
        if (log.isTraceEnabled()) log.trace("getMatchersResponseByRequestId: no FilterMatchersResponse for requestId: "+requestId);
        return null;
    }

    @POST @Path(EP_CHECK)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(tags=API_TAG_MITMPROXY,
            summary="mitmproxy: Check a connection",
            description="Called by mitmproxy at the start of the TLS handshake, determines how Bubble will handle the connection. Caller must be from localhost.",
            responses=@ApiResponse(responseCode=SC_OK, description="a ConnectionCheckResponse value")
    )
    public Response checkConnection(@Context Request req,
                                    @Context ContainerRequest request,
                                    FilterConnCheckRequest connCheckRequest) {
        final String prefix = "checkConnection: ";
        if (connCheckRequest == null || !connCheckRequest.hasServerAddr() || !connCheckRequest.hasClientAddr()) {
            if (log.isDebugEnabled()) log.debug(prefix+"invalid connCheckRequest, returning forbidden");
            return forbidden();
        }
        validateMitmCall(req);

        // is the requested IP is the same as our IP?
        final boolean isLocalIp = isForLocalIp(connCheckRequest);
        if (isLocalIp) {
            // if it is for our host or net name, passthru
            if (connCheckRequest.hasFqdns() && (connCheckRequest.hasFqdn(getThisNode().getFqdn()) || connCheckRequest.hasFqdn(getThisNetwork().getNetworkDomain()))) {
                if (log.isDebugEnabled()) log.debug(prefix + "returning passthru for LOCAL fqdn/addr=" + arrayToString(connCheckRequest.getFqdns()) + "/" + connCheckRequest.getServerAddr());
                return ok(ConnectionCheckResponse.passthru);
            }
        }

        final String vpnAddr = connCheckRequest.getClientAddr();
        final Device device = deviceService.findDeviceByIp(vpnAddr);
        if (device == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"device not found for IP "+vpnAddr+", returning not found");
            return notFound();
        } else if (log.isTraceEnabled()) {
            log.trace(prefix+"found device "+device.id()+" for IP "+vpnAddr);
        }
        final String accountUuid = device.getAccount();
        final Account account = findCaller(accountUuid);
        if (account == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"account not found for uuid "+ accountUuid +", returning not found");
            return notFound();
        }

        // if this is for a local ip, it's either a flex route or an automatic block
        // legitimate local requests would have otherwise never reached here
        if (isLocalIp) {
            if (isFlexRouteFqdn(redis, vpnAddr, connCheckRequest.getFqdns())) {
                if (log.isDebugEnabled()) log.debug(prefix + "detected flex route, allowing processing to continue");
            } else {
                final boolean showStats = showStats(accountUuid, connCheckRequest.getServerAddr(), connCheckRequest.getFqdns());
                final DeviceSecurityLevel secLevel = device.getSecurityLevel();
                if (showStats && secLevel.supportsRequestModification()) {
                    // allow it for now
                    if (log.isDebugEnabled()) log.debug(prefix + "returning noop (showStats=true, secLevel=" + secLevel + ") for LOCAL fqdn/addr=" + arrayToString(connCheckRequest.getFqdns()) + "/" + connCheckRequest.getServerAddr());
                } else {
                    if (log.isDebugEnabled()) log.debug(prefix + "returning block (showStats=" + showStats + ", secLevel=" + secLevel + ") for LOCAL fqdn/addr=" + arrayToString(connCheckRequest.getFqdns()) + "/" + connCheckRequest.getServerAddr());
                    return ok(ConnectionCheckResponse.block);
                }
            }
        }

        final List<AppMatcher> matchers = getConnCheckMatchers(accountUuid);
        final List<AppMatcher> retained = new ArrayList<>();
        for (AppMatcher matcher : matchers) {
            final BubbleApp app = appDAO.findByUuid(matcher.getApp());
            if (!app.enabled()) continue;
            final AppRule rule = ruleDAO.findByUuid(matcher.getRule());
            if (!rule.enabled()) continue;
            retained.add(matcher);
        }

        ConnectionCheckResponse checkResponse = ConnectionCheckResponse.noop;
        if (connCheckRequest.hasFqdns()) {
            final String[] fqdns = connCheckRequest.getFqdns();
            for (String fqdn : fqdns) {
                checkResponse = ruleEngine.checkConnection(account, device, retained, connCheckRequest.getClientAddr(), connCheckRequest.getServerAddr(), fqdn);
                if (checkResponse != ConnectionCheckResponse.noop) {
                    if (log.isDebugEnabled()) log.debug(prefix + "found " + checkResponse + " (breaking) for fqdn/addr=" + fqdn + "/" + connCheckRequest.getServerAddr());
                    break;
                }
            }
            if (log.isDebugEnabled()) log.debug(prefix+"returning "+checkResponse+" for fqdns/addr="+Arrays.toString(fqdns)+"/"+ connCheckRequest.getServerAddr());
            return ok(checkResponse);

        } else {
            if (log.isDebugEnabled()) log.debug(prefix+"returning noop for NO fqdns,  addr="+connCheckRequest.getServerAddr());
            return ok(ConnectionCheckResponse.noop);
        }
    }

    private final Map<String, List<AppMatcher>> connCheckMatcherCache = new ExpirationMap<>(10, HOURS.toMillis(1), ExpirationEvictionPolicy.atime);
    public List<AppMatcher> getConnCheckMatchers(String accountUuid) {
        return connCheckMatcherCache.computeIfAbsent(accountUuid, k -> matcherDAO.findByAccountAndEnabledAndConnCheck(k));
    }

    private boolean isForLocalIp(FilterConnCheckRequest connCheckRequest) {
        return connCheckRequest.hasServerAddr() && getConfiguredIps().contains(connCheckRequest.getServerAddr());
    }

    private boolean isForLocalIp(FilterMatchersRequest matchersRequest) {
        return matchersRequest.hasServerAddr() && getConfiguredIps().contains(matchersRequest.getServerAddr());
    }

    @Getter(lazy=true) private final Set<String> configuredIps = NetworkUtil.configuredIps();
    @Getter(lazy=true) private final BubbleNode thisNode = selfNodeService.getThisNode();
    @Getter(lazy=true) private final BubbleNetwork thisNetwork = selfNodeService.getThisNetwork();

    public boolean showStats(String accountUuid, String ip, String[] fqdns) {
        if (!deviceService.doShowBlockStats(accountUuid)) return false;
        for (String fqdn : fqdns) {
            final Boolean show = deviceService.doShowBlockStatsForIpAndFqdn(ip, fqdn);
            if (show != null) return show;
        }
        return true;
    }

    public boolean showStats(String accountUuid, String ip, String fqdn) {
        if (!deviceService.doShowBlockStats(accountUuid)) return false;
        final Boolean show = deviceService.doShowBlockStatsForIpAndFqdn(ip, fqdn);
        return show == null || show;
    }

    @POST @Path(EP_MATCHERS+"/{requestId}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(tags=API_TAG_MITMPROXY,
            summary="mitmproxy: Determine matchers",
            description="Called by mitmproxy after the request has been received but before the response. The matchers will determine which rules (from which apps) will apply to the request.",
            parameters=@Parameter(name="requestId", description="A unique identifier for this request", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="a FilterMatchersResponse object")
    )
    public Response selectMatchers(@Context Request req,
                                   @Context ContainerRequest request,
                                   @PathParam("requestId") String requestId,
                                   FilterMatchersRequest filterRequest) {
        boolean extraLog = requestId.contains(getLogFqdn());
        if (filterRequest == null || !filterRequest.hasRequestId() || empty(requestId) || !requestId.equals(filterRequest.getRequestId())) {
            if (log.isDebugEnabled()) log.debug("selectMatchers: no filterRequest, missing requestId, or mismatch, returning forbidden");
            else if (extraLog) log.error("selectMatchers: no filterRequest, missing requestId, or mismatch, returning forbidden");
            return forbidden();
        }
        validateMitmCall(req);

        final String prefix = "selectMatchers("+filterRequest.getRequestId()+"): ";
        if (log.isDebugEnabled()) log.debug(prefix+"starting for filterRequest="+json(filterRequest, COMPACT_MAPPER));
        else if (extraLog) log.error(prefix+"starting for filterRequest="+json(filterRequest, COMPACT_MAPPER));

        if (!filterRequest.hasClientAddr()) {
            if (log.isDebugEnabled()) log.debug(prefix+"no VPN address provided, returning no matchers");
            else if (extraLog) log.error(prefix+"no VPN address provided, returning no matchers");
            return ok(NO_MATCHERS);
        }

        final String vpnAddr = filterRequest.getClientAddr();
        final Device device = deviceService.findDeviceByIp(vpnAddr);
        if (device == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"device not found for IP "+vpnAddr+", returning no matchers");
            else if (extraLog) log.error(prefix+"device not found for IP "+vpnAddr+", returning no matchers");
            return ok(NO_MATCHERS);
        } else if (log.isTraceEnabled()) {
            log.trace(prefix+"found device "+device.id()+" for IP "+vpnAddr);
        }
        filterRequest.setDevice(device.getUuid());

        // if this is for a local ip, it's either a flex route or an automatic block
        // legitimate local requests would have otherwise been "passthru" and never reached here
        final boolean isLocalIp = isForLocalIp(filterRequest);
        final boolean showStats = showStats(device.getAccount(), filterRequest.getClientAddr(), filterRequest.getFqdn());
        if (isLocalIp) {
            if (isFlexRouteFqdn(redis, vpnAddr, filterRequest.getFqdn())) {
                if (log.isDebugEnabled()) log.debug(prefix + "detected flex route, not blocking");
            } else {
                if (filterRequest.isBrowser() && showStats) {
                    blockStats.record(filterRequest, FilterMatchDecision.abort_not_found);
                }
                if (log.isDebugEnabled()) log.debug(prefix + "returning FORBIDDEN (showBlockStats==" + showStats + ")");
                return forbidden();
            }
        }

        final FilterMatchersResponse response = getMatchersResponse(filterRequest, req, request);
        if (log.isDebugEnabled()) log.debug(prefix+"returning response: "+json(response, COMPACT_MAPPER));
        else if (extraLog) log.error(prefix+"returning response: "+json(response, COMPACT_MAPPER));

        if (filterRequest.isBrowser() && showStats) {
            blockStats.record(filterRequest, response.getDecision());
        }

        return ok(response);
    }

    private FilterMatchersResponse findMatchers(FilterMatchersRequest filterRequest, Request req, ContainerRequest request) {
        final String requestId = filterRequest.getRequestId();
        boolean extraLog = requestId.contains(getLogFqdn());
        final String prefix = "findMatchers("+ requestId +"): ";
        final Device device = findDevice(filterRequest.getDevice());
        if (device == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"findDevice("+ filterRequest.getDevice() +") returned null, returning no matchers");
            else if (extraLog) log.error(prefix+"findDevice("+ filterRequest.getDevice() +") returned null, returning no matchers");
            return NO_MATCHERS;
        }
        final String accountUuid = device.getAccount();
        final Account caller = findCaller(accountUuid);
        if (caller == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"account "+ accountUuid +" not found for device "+device.id()+", returning no matchers");
            else if (extraLog) log.error(prefix+"account "+ accountUuid +" not found for device "+device.id()+", returning no matchers");
            return NO_MATCHERS;
        }

        final String fqdn = filterRequest.getFqdn();
        final List<AppMatcher> matchers = getEnabledMatchers(requestId, accountUuid, fqdn);
        final Map<String, AppMatcher> retainMatchers;
        if (matchers.isEmpty()) {
            retainMatchers = emptyMap();
        } else {
            final String uri = filterRequest.getUri();
            retainMatchers = new HashMap<>();
            for (AppMatcher matcher : matchers) {
                if (retainMatchers.containsKey(matcher.getUuid())) continue;
                if (matcher.matchesUrl(uri)) {
                    if (matcher.hasUserAgentRegex()) {
                        if (!matcher.matchesUserAgent(filterRequest.getUserAgent())) {
                            if (log.isDebugEnabled()) log.debug(prefix+"matcher "+matcher.getName()+" with pattern "+matcher.getUrlRegex()+" found match for uri: '"+uri+"', but user-agent pattern "+matcher.getUserAgentRegex()+" does not match user-agent="+filterRequest.getUserAgent());
                            else if (extraLog) log.error(prefix+"matcher "+matcher.getName()+" with pattern "+matcher.getUrlRegex()+" found match for uri: '"+uri+"', but user-agent pattern "+matcher.getUserAgentRegex()+" does not match user-agent="+filterRequest.getUserAgent());
                            continue;
                        } else {
                            if (log.isDebugEnabled()) log.debug(prefix + "matcher " + matcher.getName() + " with pattern " + matcher.getUrlRegex() + " found match for uri: '" + uri + "' and for user-agent pattern "+matcher.getUserAgentRegex()+" for user-agent="+filterRequest.getUserAgent());
                            else if (extraLog) log.error(prefix + "matcher " + matcher.getName() + " with pattern " + matcher.getUrlRegex() + " found match for uri: '" + uri + "' and for user-agent pattern "+matcher.getUserAgentRegex()+" for user-agent="+filterRequest.getUserAgent());
                        }
                    } else {
                        if (log.isDebugEnabled()) log.debug(prefix + "matcher " + matcher.getName() + " with pattern " + matcher.getUrlRegex() + " found match for uri: '" + uri + "'");
                        else if (extraLog) log.error(prefix + "matcher " + matcher.getName() + " with pattern " + matcher.getUrlRegex() + " found match for uri: '" + uri + "'");
                    }
                    final FilterMatchDecision matchResponse = ruleEngine.preprocess(filterRequest, req, request, caller, device, matcher);
                    switch (matchResponse) {
                        case abort_ok:        return FilterMatchersResponse.ABORT_OK;
                        case abort_not_found: return FilterMatchersResponse.ABORT_NOT_FOUND;
                        case no_match:        break;
                        case pass_thru:       return FilterMatchersResponse.PASS_THRU;
                        case match:           retainMatchers.put(matcher.getUuid(), matcher); break;
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug(prefix+"matcher "+matcher.getName()+" with pattern "+matcher.getUrlRegex()+" did NOT match uri: '"+uri+"'");
                    else if (extraLog) log.error(prefix+"matcher "+matcher.getName()+" with pattern "+matcher.getUrlRegex()+" did NOT match uri: '"+uri+"'");
                }
            }
        }

        final FilterMatchersResponse response = new FilterMatchersResponse()
                .setDecision(empty(retainMatchers) ? FilterMatchDecision.no_match : FilterMatchDecision.match)
                .setRequest(filterRequest)
                .setMatchers(empty(retainMatchers) ? Collections.emptyList() : new ArrayList<>(retainMatchers.values()));

        if (log.isDebugEnabled()) log.debug(prefix+"preprocess decision for "+filterRequest.getUrl()+": "+response+", retainMatchers="+names(retainMatchers.values()));
        else if (extraLog) log.error(prefix+"preprocess decision for "+filterRequest.getUrl()+": "+response+", retainMatchers="+names(retainMatchers.values()));

        return response;
    }

    private List<AppMatcher> getEnabledMatchers(String requestId, String accountUuid, String fqdn) {
        boolean extraLog = fqdn.contains(getLogFqdn());
        final String prefix = "getEnabledMatchers("+requestId+"): ";
        List<AppMatcher> matchers = matcherDAO.findByAccountAndFqdnAndEnabledAndRequestCheck(accountUuid, fqdn);
        if (log.isTraceEnabled()) log.trace(prefix+"checking all enabled matchers for fqdn: "+json(matchers, COMPACT_MAPPER));
        else if (extraLog) log.error(prefix+"checking all enabled matchers for fqdn: "+json(matchers, COMPACT_MAPPER));
        matchers = matchers.stream()
                .filter(m -> appDAO.findByAccountAndId(accountUuid, m.getApp()).enabled()).collect(Collectors.toList());
        if (log.isTraceEnabled()) log.trace(prefix+"after removing disabled apps, enabled matchers for fqdn: "+json(matchers, COMPACT_MAPPER));
        else if (extraLog) log.error(prefix+"after removing disabled apps, enabled matchers for fqdn: "+json(matchers, COMPACT_MAPPER));
        if (matchers.isEmpty()) return matchers;

        matchers = matchers.stream()
                .filter(m -> {
                    final AppSite site = siteDAO.findByAccountAndAppAndId(accountUuid, m.getApp(), m.getSite());
                    if (site == null) {
                        if (log.isWarnEnabled()) log.warn(prefix+"site "+m.getSite()+" not found for matcher "+m.getName()+"/"+m.getUuid());
                        else if (extraLog) log.error(prefix+"site "+m.getSite()+" not found for matcher "+m.getName()+"/"+m.getUuid());
                        return false;
                    }
                    return site.enabled();
                }).collect(Collectors.toList());
        if (log.isTraceEnabled()) log.trace(prefix+"after removing disabled sites, enabled matchers for fqdn: "+json(matchers, COMPACT_MAPPER));
        else if (extraLog) log.error(prefix+"after removing disabled sites, enabled matchers for fqdn: "+json(matchers, COMPACT_MAPPER));
        if (matchers.isEmpty()) return matchers;

        matchers = matchers.stream()
                .filter(m -> {
                    final AppRule rule = ruleDAO.findByAccountAndAppAndId(accountUuid, m.getApp(), m.getRule());
                    if (rule == null) {
                        if (log.isWarnEnabled()) log.warn(prefix+"rule "+m.getRule()+" not found for matcher "+m.getName()+"/"+m.getUuid());
                        else if (extraLog) log.error(prefix+"rule "+m.getRule()+" not found for matcher "+m.getName()+"/"+m.getUuid());
                        return false;
                    }
                    return rule.enabled();
                }).collect(Collectors.toList());
        if (log.isTraceEnabled()) log.trace(prefix+"after removing disabled rules, enabled matchers for fqdn: "+json(matchers, COMPACT_MAPPER));
        else if (log.isDebugEnabled()) log.debug(prefix+"found "+matchers.size()+" candidate matchers: "+names(matchers));
        else if (extraLog) log.error(prefix+"found "+matchers.size()+" candidate matchers: "+names(matchers));
        return matchers;
    }

    @DELETE
    @Produces(APPLICATION_JSON)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_MITMPROXY, API_TAG_DEVICES, API_TAG_UTILITY},
            summary="Flush caches",
            description="Flushes caches of: connection decisions, matchers and rules",
            responses=@ApiResponse(responseCode=SC_OK, description="a JSON object showing what was flushed")
    )
    public Response flushCaches(@Context ContainerRequest request) {
        final Account caller = userPrincipal(request);
        if (!caller.admin()) return forbidden();

        final int connCheckMatcherCacheSize = connCheckMatcherCache.size();
        connCheckMatcherCache.clear();

        // disable redirect flushing for now -- it works well and it's a lot of work
        // final Long redirectCacheSize = getRedirectCache().del_matching("*");

        final Map<Object, Object> flushes = ruleEngine.flushCaches();
        flushes.put("connCheckMatchersCache", connCheckMatcherCacheSize);
        // flushes.put("redirectCache", redirectCacheSize == null ? 0 : redirectCacheSize);
        return ok(flushes);
    }

    @DELETE @Path(EP_MATCHERS)
    @Produces(APPLICATION_JSON)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_MITMPROXY, API_TAG_DEVICES, API_TAG_UTILITY},
            summary="Flush matchers",
            description="Flushes matchers only",
            responses=@ApiResponse(responseCode=SC_OK, description="an integer representing how many cache entries were flushed")
    )
    public Response flushMatchers(@Context ContainerRequest request) {
        final Account caller = userPrincipal(request);
        if (!caller.admin()) return forbidden();
        return ok(ruleEngine.flushMatchers());
    }

    @POST @Path(EP_APPLY+"/{requestId}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    @Operation(tags=API_TAG_MITMPROXY,
            summary="mitmproxy: Filter response",
            description="Called by mitmproxy while reading the response. As mitmproxy reads chunks of the response, it sends the bytes here for filtering, then relays the response to the device. The `encoding`, `type`, and `length` params are optional and are only used on the first call. When mitmproxy reaches the end of the response, it sends the `last` param with a value of `true` to indicate that no more data is coming. This allows Bubble to flush any caches and return any response data that might still be waiting.",
            parameters={
                @Parameter(name="requestId", description="the unique identifier for the request", required=true),
                @Parameter(name="encoding", description="the Content-Encoding of the data"),
                @Parameter(name="type", description="the Content-Type of the data"),
                @Parameter(name="length", description="the Content-Length of the data"),
                @Parameter(name="last", description="true if this is the last chunk of bytes mitmproxy will be sending, false if there are more chunks still to send"),
            },
            responses=@ApiResponse(responseCode=SC_OK, description="bytes of the response")
    )
    public Response filterHttp(@Context Request req,
                               @Context ContainerRequest request,
                               @PathParam("requestId") String requestId,
                               @QueryParam("encoding") String contentEncoding,
                               @QueryParam("type") String contentType,
                               @QueryParam("length") Long contentLength,
                               @QueryParam("last") Boolean last) throws IOException {
        validateMitmCall(req);

        requestId = trimQuotes(requestId);
        if (empty(requestId)) {
            if (log.isDebugEnabled()) log.debug("filterHttp: no requestId provided, returning passthru");
            return passthru(request);
        }
        final String prefix = "filterHttp("+requestId+"): ";

        final FilterMatchersResponse matchersResponse = getMatchersResponseByRequestId(requestId);
        if (matchersResponse == null) {
            if (log.isWarnEnabled()) log.warn(prefix+"FilterMatchersResponse not found, returning passthru");
            return passthru(request);
        }

        if (log.isTraceEnabled()) log.trace(prefix+"found FilterMatchersResponse: "+json(matchersResponse, COMPACT_MAPPER));
        if (matchersResponse.hasAbort()) {
            if (log.isWarnEnabled()) log.warn(prefix+"FilterMatchersResponse has abort code "+matchersResponse.httpStatus()+", MITM should have aborted. We are aborting now.");
            return status(matchersResponse.httpStatus());

        } else if (!matchersResponse.hasRequestCheckMatchers()) {
            if (log.isWarnEnabled()) log.warn(prefix+"FilterMatchersResponse has no requestCheck matchers, returning passthru");
            return passthru(request);

        } else if (!matchersResponse.hasRequest()) {
            if (log.isWarnEnabled()) log.warn(prefix + "FilterMatchersResponse has no request, returning passthru");
            return passthru(request);
        }

        final boolean isLast = bool(last);

        // can we handle the encoding?
        final HttpContentEncodingType encoding;
        if (empty(contentEncoding)) {
            encoding = null;
        } else {
            try {
                encoding = HttpContentEncodingType.fromString(contentEncoding);
            } catch (Exception e) {
                if (log.isWarnEnabled()) log.warn(prefix+"invalid encoding ("+contentEncoding+"), returning passthru");
                return passthru(request);
            }
        }

        // mitmproxy provides Content-Length, which helps us right-size the input byte buffer
        final String contentLengthHeader = req.getHeader(CONTENT_LENGTH);
        Integer chunkLength;
        try {
            chunkLength = empty(contentLengthHeader) ? null : Integer.parseInt(contentLengthHeader);
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug(prefix+"error parsing Content-Length ("+contentLengthHeader+"): "+shortError(e));
            chunkLength = null;
        }

        final FilterMatchDecision decision = matchersResponse.getDecision();
        if (decision != FilterMatchDecision.match) {
            switch (decision) {
                case no_match: case pass_thru:
                    if (log.isWarnEnabled()) log.warn(prefix + "FilterMatchersResponse decision was no_match/pass_thru (should not have received this): "+ decision +", returning passthru");
                    return passthru(request);
                case abort_not_found:
                case abort_ok:
                    if (log.isWarnEnabled()) log.warn(prefix + "FilterMatchersResponse decision was abort: "+ decision +", returning "+matchersResponse.httpStatus());
                    return status(matchersResponse.httpStatus());
                default:
                    if (log.isWarnEnabled()) log.warn(prefix + "FilterMatchersResponse decision was unknown: "+ decision +", returning passthru");
                    return passthru(request);
            }

        } else if (!matchersResponse.getRequest().hasDevice()) {
            if (log.isWarnEnabled()) log.warn(prefix+"FilterMatchersResponse has no device, returning passthru");
            return passthru(request);
        }

        matchersResponse.setRequestId(requestId);

        FilterHttpRequest filterRequest = getActiveRequest(requestId);
        if (filterRequest == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"filterRequest not found, initiating...");
            if (empty(contentType)) {
                if (log.isDebugEnabled()) log.debug(prefix+"filterRequest not found, and no contentType provided, returning passthru");
                return passthru(request);
            }
            if (!isContentTypeMatch(matchersResponse, contentType)) {
                if (log.isDebugEnabled()) log.debug(prefix+"none of the "+matchersResponse.getMatchers().size()+" matchers matched contentType="+contentType+", returning passthru");
                return passthru(request);
            }

            final Device device = findDevice(matchersResponse.getRequest().getDevice());
            if (device == null) {
                if (log.isDebugEnabled()) log.debug(prefix+"device "+matchersResponse.getRequest().getDevice()+" not found, returning passthru");
                return passthru(request);
            } else {
                if (log.isTraceEnabled()) log.trace(prefix+"found device: "+device.id()+" ... ");
            }
            final Account caller = findCaller(device.getAccount());
            if (caller == null) {
                if (log.isDebugEnabled()) log.debug(prefix+"account "+device.getAccount()+" not found, returning passthru");
                return passthru(request);
            }
            filterRequest = new FilterHttpRequest()
                    .setId(requestId)
                    .setMatchersResponse(matchersResponse)
                    .setDevice(device)
                    .setAccount(caller)
                    .setEncoding(encoding)
                    .setContentType(contentType)
                    .setContentLength(contentLength)
                    .setContentSecurityPolicy(req.getHeader(CONTENT_SECURITY_POLICY));
            if (log.isTraceEnabled()) log.trace(prefix+"start filterRequest="+json(filterRequest, COMPACT_MAPPER));
            getActiveRequestCache().set(requestId, json(filterRequest, COMPACT_MAPPER), EX, ACTIVE_REQUEST_TIMEOUT);
        } else {
            if (!isContentTypeMatch(matchersResponse, filterRequest.getContentType())) {
                if (log.isInfoEnabled()) log.info(prefix+"none of the "+matchersResponse.getMatchers().size()+" matchers matched contentType="+filterRequest.getContentType()+", returning passthru");
                return passthru(request);
            }

            if (log.isTraceEnabled()) {
                if (isLast) {
                    log.trace(prefix+"last filterRequest=" + json(filterRequest, COMPACT_MAPPER));
                } else {
                    log.trace(prefix+"continuing filterRequest=" + json(filterRequest, COMPACT_MAPPER));
                }
            }
        }

        return ruleEngine.applyRulesToChunkAndSendResponse(request, filterRequest, chunkLength, isLast);
    }

    private void validateMitmCall(Request req) {
        // only mitmproxy is allowed to call us, and this should always be a local address
        final String mitmAddr = req.getRemoteAddr();
        if (!isLocalIpv4(mitmAddr)) {
            if (log.isDebugEnabled()) log.debug("validateMitmCall: mitmAddr ("+mitmAddr+") was not local IPv4, returning forbidden");
            throw forbiddenEx();
        }
    }

    public boolean isContentTypeMatch(FilterMatchersResponse matchersResponse, String ct) {
        final String prefix = "isContentTypeMatch("+matchersResponse.getRequest().getRequestId()+"): ";
        final List<AppMatcher> matchers = matchersResponse.getMatchers();
        for (AppMatcher m : matchers) {
            if (log.isDebugEnabled()) log.debug(prefix+"checking contentType match, matcher.contentTypeRegex="+m.getContentTypeRegex()+", contentType="+ct);
            if (m.matchesContentType(ct)) {
                return true;
            }
        }
        return false;
    }

    public Response passthru(@Context ContainerRequest request) { return ruleEngine.passthru(request); }

    @Path(EP_DATA+"/{requestId}/{matcherId}")
    public FilterDataResource getMatcherDataResource(@Context Request req,
                                                     @Context ContainerRequest ctx,
                                                     @PathParam("requestId") String requestId,
                                                     @PathParam("matcherId") String matcherId) {
        final FilterSubContext filterCtx = new FilterSubContext(req, requestId);
        if (!filterCtx.request.hasMatcher(matcherId)) throw notFoundEx(matcherId);

        final Account account = filterCtx.request.getAccount();
        account.setMtime(0);  // only create one FilterDataResource
        final AppMatcher matcher = matcherDAO.findByAccountAndId(account.getUuid(), matcherId);
        if (matcher == null) throw notFoundEx(matcherId);

        final Device device = filterCtx.request.getDevice();
        device.setMtime(0);  // only create one FilterDataResource
        return configuration.subResource(FilterDataResource.class, account, device, matcher);
    }

    @GET @Path(EP_STATUS+"/{requestId}")
    @Produces(APPLICATION_JSON)
    @Operation(tags=API_TAG_APP_RUNTIME,
            summary="app runtime: Get a BlockStatsSummary for the current request",
            description="Get a BlockStatsSummary for the current request",
            parameters=@Parameter(name="requestId", description="The unique `requestId` for the request", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="a BlockStatsSummary object")
    )
    public Response getRequestStatus(@Context Request req,
                                     @Context ContainerRequest ctx,
                                     @PathParam("requestId") String requestId) {
        final FilterSubContext filterCtx = new FilterSubContext(req, requestId);
        final BlockStatsSummary summary = blockStats.getSummary(requestId);
        if (summary == null) return notFound(requestId);
        return ok(summary);
    }

    @GET @Path(EP_FLEX_ROUTERS+"/{fqdn}")
    @Produces(APPLICATION_JSON)
    @Operation(tags=API_TAG_MITMPROXY,
            summary="mitmproxy: Get a flex router",
            description="Called by mitmproxty when a flex router is required. May return a FlexRouter object or, if no routers are available, a FlexRouterInfo object whose `errorHtml` property contains instructions on what to do next.",
            parameters=@Parameter(name="fqdn", description="The hostname that requires a flex router", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="a FlexRouter object or a FlexRouterInfo object whose `errorHtml` property contains instructions on what to do next.")
    )
    public Response getFlexRouter(@Context Request req,
                                  @Context ContainerRequest ctx,
                                  @PathParam("fqdn") String fqdn) {
        final String publicIp = getRemoteAddr(req);
        final Device device = deviceService.findDeviceByIp(publicIp);
        if (device == null) {
            log.warn("getFlexRouter: device not found with IP: "+publicIp);
            return notFound();
        }

        final DeviceStatus deviceStatus = deviceService.getDeviceStatus(device.getUuid());
        if (!deviceStatus.hasIp()) {
            log.error("getFlexRouter: no device status for device: "+device);
            return notFound();
        }
        final String vpnIp = deviceStatus.getIp();

        if (log.isDebugEnabled()) log.debug("getFlexRouter: finding routers for vpnIp="+vpnIp);
        Collection<FlexRouterInfo> routers = flexRouterService.selectClosestRouter(device.getAccount(), vpnIp, publicIp);

        if (log.isDebugEnabled()) log.debug("getFlexRouter: found router(s) for vpnIp="+vpnIp+": "+json(routers, COMPACT_MAPPER));
        if (routers.isEmpty()) {
            final Account account = accountDAO.findByUuid(device.getAccount());
            return ok(missingFlexRouter(account, device, fqdn, messageService, configuration.getHandlebars()));
        }
        return ok(routers.iterator().next().initAuth());
    }

    @POST @Path(EP_LOGS+"/{requestId}")
    @Produces(APPLICATION_JSON)
    @Operation(tags=API_TAG_APP_RUNTIME,
            summary="app runtime: write to log file",
            description="Useful when developing and debugging apps, your app can write to the server logfile using this API call",
            parameters=@Parameter(name="requestId", description="The unique `requestId` for the request", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="empty JSON object indicates success")
    )
    public Response requestLog(@Context Request req,
                               @Context ContainerRequest ctx,
                               @PathParam("requestId") String requestId,
                               JsonNode logData) {
        final FilterSubContext filterCtx = new FilterSubContext(req, requestId);
        log.error(" >>>>> REQUEST-LOG("+requestId+"): "+json(logData, COMPACT_MAPPER));
        return ok_empty();
    }

    public static final String REDIS_PREFIX_REDIRECT_CACHE = "followLink_";
    @Getter(lazy=true) private final RedisService redirectCache = redis.prefixNamespace(REDIS_PREFIX_REDIRECT_CACHE);

    @POST @Path(EP_FOLLOW+"/{requestId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(tags=API_TAG_APP_RUNTIME,
            summary="app runtime: chase redirects",
            description="Apps can request that the Bubble server chase down redirects to find the real link. Bubble can then cache these so we avoid chasing the same link more than once.",
            parameters=@Parameter(name="requestId", description="The unique `requestId` for the request", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="a String representing the real URL to use")
    )
    public Response followLink(@Context Request req,
                               @Context ContainerRequest ctx,
                               @PathParam("requestId") String requestId,
                               JsonNode urlNode) {
        final FilterSubContext filterCtx = new FilterSubContext(req, requestId);
        final RedisService cache = getRedirectCache();
        final String url = urlNode.textValue();
        final String cacheKey = sha256_hex(url);
        final String cachedValue = cache.get(cacheKey);
        if (cachedValue != null) return ok(cachedValue);

        final String result = chaseRedirects(url);
        cache.set(cacheKey, result, EX, DAYS.toMillis(365));
        return ok(result);
    }

    public static final String CLIENT_HEADER_PREFIX = "X-Bubble-Client-Header-";

    public static final String[] EXCLUDED_CLIENT_HEADERS = {
            ACCEPT.toLowerCase(),
            CONTENT_TYPE.toLowerCase(), CONTENT_LENGTH.toLowerCase(),
            CONTENT_ENCODING.toLowerCase(), TRANSFER_ENCODING.toLowerCase()
    };

    @POST @Path(EP_FOLLOW_AND_APPLY_REGEX+"/{requestId}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(tags=API_TAG_APP_RUNTIME,
            summary="app runtime: chase redirect then apply regex",
            description="Some redirects land us on a page whose URL is not what we want (it is ugly in some way), but whose nicer URL is within the page itself. This method can follow redirects and apply a regex and determined by the FollowThenApplyRegex object in the request",
            parameters=@Parameter(name="requestId", description="The unique `requestId` for the request", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="a String representing the real URL to use")
    )
    public Response followLinkThenApplyRegex(@Context Request req,
                                             @Context ContainerRequest ctx,
                                             @PathParam("requestId") String requestId,
                                             FollowThenApplyRegex follow) {
        final FilterSubContext filterCtx = new FilterSubContext(req, requestId);
        final RedisService cache = getRedirectCache();
        final String followJson = json(follow);
        final String cacheKey = sha256_hex(followJson);
        final String cachedValue = cache.get(cacheKey);
        if (cachedValue != null) return ok(cachedValue);

        // collect client headers
        final List<NameAndValue> headers = new ArrayList<>();
        for (String name : req.getHeaderNames()) {
            if (name.toLowerCase().startsWith(CLIENT_HEADER_PREFIX.toLowerCase())) {
                final String value = req.getHeader(name);
                final String realName = name.substring(CLIENT_HEADER_PREFIX.length());
                if (ArrayUtils.indexOf(EXCLUDED_CLIENT_HEADERS, realName.toLowerCase()) == -1) {
                    headers.add(new NameAndValue(realName, value));
                }
            }
        }
        headers.add(new NameAndValue(ACCEPT, "*/*"));
        final List<Map<Integer, String>> matches
                = applyRegexToUrl(follow.getUrl(), headers, follow.getRegex(), Arrays.asList(follow.getGroups()));
        if (log.isWarnEnabled()) log.warn("followLink(" + follow.getUrl() + ") returning: " + json(matches));
        final String result = matches == null ? EMPTY_JSON_ARRAY : json(matches);
        cache.set(cacheKey, result, EX, DAYS.toMillis(365));
        return ok(result);
    }

    @Path(EP_ASSETS+"/{requestId}/{appId}")
    public AppAssetsResource getAppAssetsResource(@Context Request req,
                                                  @Context ContainerRequest ctx,
                                                  @PathParam("requestId") String requestId,
                                                  @PathParam("appId") String appId) {

        final FilterSubContext filterCtx = new FilterSubContext(req, requestId);
        final BubbleApp app = appDAO.findByAccountAndId(filterCtx.request.getAccount().getUuid(), appId);
        if (app == null) throw notFoundEx(appId);

        if (!filterCtx.request.hasApp(app.getUuid())) throw notFoundEx(appId);

        return configuration.subResource(AppAssetsResource.class, filterCtx.request.getAccount().getLocale(), app);
    }

    @Path(EP_MESSAGES+"/{requestId}/{appId}")
    public FilterAppMessagesResource getAppMessagesResource(@Context Request req,
                                                            @Context ContainerRequest ctx,
                                                            @PathParam("requestId") String requestId,
                                                            @PathParam("appId") String appId) {

        final FilterSubContext filterCtx = new FilterSubContext(req, requestId);
        final BubbleApp app = appDAO.findByAccountAndId(filterCtx.request.getAccount().getUuid(), appId);
        if (app == null) throw notFoundEx(appId);

        if (!filterCtx.request.hasApp(app.getUuid())) throw notFoundEx(appId);

        return configuration.subResource(FilterAppMessagesResource.class, filterCtx.request.getAccount(), app);
    }

    private class FilterSubContext {
        public FilterHttpRequest request;

        public FilterSubContext(Request req, String requestId) {
            // only mitmproxy is allowed to call us, and this should always be a local address
            final String mitmAddr = req.getRemoteAddr();
            if (!isLocalIpv4(mitmAddr)) throw forbiddenEx();

            if (empty(requestId)) throw notFoundEx();

            request = getActiveRequest(requestId);
            if (request == null) throw notFoundEx(requestId);
        }
    }

}