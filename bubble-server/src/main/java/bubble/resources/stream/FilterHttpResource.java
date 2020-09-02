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
import bubble.rule.FilterMatchDecision;
import bubble.server.BubbleConfiguration;
import bubble.service.block.BlockStatsService;
import bubble.service.block.BlockStatsSummary;
import bubble.service.boot.SelfNodeService;
import bubble.service.cloud.DeviceIdService;
import bubble.service.stream.ConnectionCheckResponse;
import bubble.service.stream.StandardRuleEngineService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationEvictionPolicy;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.http.HttpContentEncodingType;
import org.cobbzilla.util.http.HttpUtil;
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
import static bubble.service.stream.HttpStreamDebug.getLogFqdn;
import static bubble.service.stream.StandardRuleEngineService.MATCHERS_CACHE_TIMEOUT;
import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.*;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static org.cobbzilla.util.collection.ArrayUtil.arrayToString;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.isLocalIpv4;
import static org.cobbzilla.util.string.StringUtil.trimQuotes;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.model.NamedEntity.names;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

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
    @Autowired private DeviceIdService deviceIdService;
    @Autowired private RedisService redis;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private SelfNodeService selfNodeService;
    @Autowired private BlockStatsService blockStats;

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
    public Response checkConnection(@Context Request req,
                                    @Context ContainerRequest request,
                                    FilterConnCheckRequest connCheckRequest) {
        final String prefix = "checkConnection: ";
        if (connCheckRequest == null || !connCheckRequest.hasAddr() || !connCheckRequest.hasRemoteAddr()) {
            if (log.isDebugEnabled()) log.debug(prefix+"invalid connCheckRequest, returning forbidden");
            return forbidden();
        }
        validateMitmCall(req);

        // is the requested IP is the same as our IP?
        final boolean isLocalIp = isForLocalIp(connCheckRequest);
        if (isLocalIp) {
            // if it is for our host or net name, passthru
            if (connCheckRequest.hasFqdns() && (connCheckRequest.hasFqdn(getThisNode().getFqdn()) || connCheckRequest.hasFqdn(getThisNetwork().getNetworkDomain()))) {
                if (log.isDebugEnabled()) log.debug(prefix + "returning passthru for LOCAL fqdn/addr=" + arrayToString(connCheckRequest.getFqdns()) + "/" + connCheckRequest.getAddr());
                return ok(ConnectionCheckResponse.passthru);
            }
        }

        final String vpnAddr = connCheckRequest.getRemoteAddr();
        final Device device = deviceIdService.findDeviceByIp(vpnAddr);
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

        if (isLocalIp) {
            final boolean showStats = showStats(accountUuid, connCheckRequest.getAddr(), connCheckRequest.getFqdns());
            final DeviceSecurityLevel secLevel = device.getSecurityLevel();
            if (showStats && secLevel.supportsRequestModification()) {
                // allow it for now
                if (log.isDebugEnabled()) log.debug(prefix + "returning noop (showStats=true, secLevel="+secLevel+") for LOCAL fqdn/addr=" + arrayToString(connCheckRequest.getFqdns()) + "/" + connCheckRequest.getAddr());
                return ok(ConnectionCheckResponse.noop);
            } else {
                if (log.isDebugEnabled()) log.debug(prefix + "returning block (showStats="+showStats+", secLevel="+secLevel+") for LOCAL fqdn/addr=" + arrayToString(connCheckRequest.getFqdns()) + "/" + connCheckRequest.getAddr());
                return ok(ConnectionCheckResponse.block);
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
                checkResponse = ruleEngine.checkConnection(account, device, retained, connCheckRequest.getAddr(), fqdn);
                if (checkResponse != ConnectionCheckResponse.noop) {
                    if (log.isDebugEnabled()) log.debug(prefix + "found " + checkResponse + " (breaking) for fqdn/addr=" + fqdn + "/" + connCheckRequest.getAddr());
                    break;
                }
            }
            if (log.isDebugEnabled()) log.debug(prefix+"returning "+checkResponse+" for fqdns/addr="+Arrays.toString(fqdns)+"/"+ connCheckRequest.getAddr());
            return ok(checkResponse);

        } else {
            if (log.isDebugEnabled()) log.debug(prefix+"returning noop for NO fqdns,  addr="+connCheckRequest.getAddr());
            return ok(ConnectionCheckResponse.noop);
        }
    }

    private final Map<String, List<AppMatcher>> connCheckMatcherCache = new ExpirationMap<>(10, HOURS.toMillis(1), ExpirationEvictionPolicy.atime);
    public List<AppMatcher> getConnCheckMatchers(String accountUuid) {
        return connCheckMatcherCache.computeIfAbsent(accountUuid, k -> matcherDAO.findByAccountAndEnabledAndConnCheck(k));
    }

    private boolean isForLocalIp(FilterConnCheckRequest connCheckRequest) {
        return connCheckRequest.hasAddr() && getConfiguredIps().contains(connCheckRequest.getAddr());
    }

    private boolean isForLocalIp(FilterMatchersRequest matchersRequest) {
        return matchersRequest.hasServerAddr() && getConfiguredIps().contains(matchersRequest.getServerAddr());
    }

    @Getter(lazy=true) private final Set<String> configuredIps = NetworkUtil.configuredIps();
    @Getter(lazy=true) private final BubbleNode thisNode = selfNodeService.getThisNode();
    @Getter(lazy=true) private final BubbleNetwork thisNetwork = selfNodeService.getThisNetwork();

    public boolean showStats(String accountUuid, String ip, String[] fqdns) {
        if (!deviceIdService.doShowBlockStats(accountUuid)) return false;
        for (String fqdn : fqdns) {
            final Boolean show = deviceIdService.doShowBlockStatsForIpAndFqdn(ip, fqdn);
            if (show != null) return show;
        }
        return true;
    }

    public boolean showStats(String accountUuid, String ip, String fqdn) {
        if (!deviceIdService.doShowBlockStats(accountUuid)) return false;
        final Boolean show = deviceIdService.doShowBlockStatsForIpAndFqdn(ip, fqdn);
        return show == null || show;
    }

    @POST @Path(EP_MATCHERS+"/{requestId}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
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
        final Device device = deviceIdService.findDeviceByIp(vpnAddr);
        if (device == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"device not found for IP "+vpnAddr+", returning no matchers");
            else if (extraLog) log.error(prefix+"device not found for IP "+vpnAddr+", returning no matchers");
            return ok(NO_MATCHERS);
        } else if (log.isTraceEnabled()) {
            log.trace(prefix+"found device "+device.id()+" for IP "+vpnAddr);
        }
        filterRequest.setDevice(device.getUuid());

        // if this is for a local ip, it's an automatic block
        // legitimate local requests would have been passthru and never reached here
        final boolean isLocalIp = isForLocalIp(filterRequest);
        final boolean showStats = showStats(device.getAccount(), filterRequest.getClientAddr(), filterRequest.getFqdn());
        if (isLocalIp) {
            if (filterRequest.isBrowser() && showStats) {
                blockStats.record(filterRequest, FilterMatchDecision.abort_not_found);
            }
            if (log.isDebugEnabled()) log.debug(prefix + "returning FORBIDDEN (showBlockStats=="+ showStats +")");
            return forbidden();
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
    public Response flushCaches(@Context ContainerRequest request) {
        final Account caller = userPrincipal(request);
        if (!caller.admin()) return forbidden();
        connCheckMatcherCache.clear();
        return ok(ruleEngine.flushCaches());
    }

    @DELETE @Path(EP_MATCHERS)
    @Produces(APPLICATION_JSON)
    public Response flushMatchers(@Context ContainerRequest request) {
        final Account caller = userPrincipal(request);
        if (!caller.admin()) return forbidden();
        return ok(ruleEngine.flushMatchers());
    }

    @POST @Path(EP_APPLY+"/{requestId}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
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
    public Response getRequestStatus(@Context Request req,
                                     @Context ContainerRequest ctx,
                                     @PathParam("requestId") String requestId) {
        final FilterSubContext filterCtx = new FilterSubContext(req, requestId);
        final BlockStatsSummary summary = blockStats.getSummary(requestId);
        if (summary == null) return notFound(requestId);
        return ok(summary);
    }

    @POST @Path(EP_LOGS+"/{requestId}")
    public Response requestLog(@Context Request req,
                               @Context ContainerRequest ctx,
                               @PathParam("requestId") String requestId,
                               JsonNode logData) {
        final FilterSubContext filterCtx = new FilterSubContext(req, requestId);
        log.error(" >>>>> REQUEST-LOG("+requestId+"): "+json(logData, COMPACT_MAPPER));
        return ok_empty();
    }

    private final Map<String, String> redirectCache
            = new ExpirationMap<>(1000, DAYS.toMillis(3), ExpirationEvictionPolicy.atime);

    @POST @Path(EP_FOLLOW+"/{requestId}")
    public Response followLink(@Context Request req,
                               @Context ContainerRequest ctx,
                               @PathParam("requestId") String requestId,
                               JsonNode urlNode) {
        final FilterSubContext filterCtx = new FilterSubContext(req, requestId);
        return ok(redirectCache.computeIfAbsent(urlNode.textValue(), HttpUtil::chaseRedirects));
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