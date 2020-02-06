package bubble.resources.stream;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.AppDataDAO;
import bubble.dao.app.AppMatcherDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.app.AppDataFormat;
import bubble.model.app.AppMatcher;
import bubble.model.device.Device;
import bubble.rule.FilterMatchDecision;
import bubble.service.cloud.DeviceIdService;
import bubble.service.stream.StandardRuleEngineService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.http.HttpContentEncodingType;
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
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.isLocalIpv4;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Path(FILTER_HTTP_ENDPOINT)
@Service @Slf4j
public class FilterHttpResource {

    @Autowired private AccountDAO accountDAO;
    @Autowired private StandardRuleEngineService ruleEngine;
    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private DeviceDAO deviceDAO;
    @Autowired private DeviceIdService deviceIdService;
    @Autowired private AppDataDAO dataDAO;

    @Autowired private RedisService redis;

    private static final long ACTIVE_REQUEST_TIMEOUT = HOURS.toSeconds(4);

    @Getter(lazy=true) private final RedisService activeRequestCache = redis.prefixNamespace(getClass().getSimpleName()+".requests");

    public FilterHttpRequest getActiveRequest(String requestId) {
        final String json = getActiveRequestCache().get(requestId);
        if (json == null) return null;
        return json(json, FilterHttpRequest.class);
    }

    private Map<String, Account> accountCache = new ExpirationMap<>(MINUTES.toMillis(10));
    public Account findCaller(String accountUuid) {
        return accountCache.computeIfAbsent(accountUuid, uuid -> accountDAO.findByUuid(uuid));
    }

    private Map<String, Device> deviceCache = new ExpirationMap<>(MINUTES.toMillis(10));
    public Device findDevice(String deviceUuid) {
        return deviceCache.computeIfAbsent(deviceUuid, uuid -> deviceDAO.findByUuid(uuid));
    }

    public static final long MATCHERS_CACHE_TIMEOUT = MINUTES.toSeconds(15);
    @Getter(lazy=true) private final RedisService matchersCache = redis.prefixNamespace(getClass().getSimpleName()+".matchers");

    private FilterMatchersResponse getMatchersResponse(FilterMatchersRequest filterRequest,
                                                       Request req,
                                                       ContainerRequest request) {
        final RedisService cache = getMatchersCache();

        final String requestId = filterRequest.getRequestId();
        final String prefix = "getMatchersResponse("+requestId+"): ";
        final String cacheKey = filterRequest.cacheKey();
        final String matchersJson = cache.get(cacheKey);
        if (matchersJson != null) {
            final FilterMatchersResponse cached = json(matchersJson, FilterMatchersResponse.class);
            cache.set(requestId, json(cached.setRequestId(requestId), COMPACT_MAPPER), EX, MATCHERS_CACHE_TIMEOUT);
            if (log.isTraceEnabled()) log.trace(prefix+"found cached response for cacheKey="+cacheKey+" and set for requestId "+requestId+": "+json(cached, COMPACT_MAPPER));
            return cached;
        }

        final FilterMatchersResponse response = findMatchers(filterRequest, req, request);
        if (log.isTraceEnabled()) log.trace(prefix+"writing cache-miss to redis under keys "+cacheKey+" and "+requestId+": "+json(response, COMPACT_MAPPER));
        cache.set(cacheKey, json(response, COMPACT_MAPPER), EX, MATCHERS_CACHE_TIMEOUT);
        cache.set(requestId, json(response, COMPACT_MAPPER), EX, MATCHERS_CACHE_TIMEOUT);
        return response;
    }

    private FilterMatchersResponse getMatchersResponseByRequestId(String requestId) {
        final RedisService cache = getMatchersCache();
        final String matchersJson = cache.get(requestId);
        if (matchersJson != null) return json(matchersJson, FilterMatchersResponse.class);
        if (log.isTraceEnabled()) log.trace("getMatchersResponseByRequestId: no FilterMatchersResponse for requestId: "+requestId);
        return null;
    }

    @POST @Path(EP_MATCHERS+"/{requestId}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response selectMatchers(@Context Request req,
                                   @Context ContainerRequest request,
                                   @PathParam("requestId") String requestId,
                                   FilterMatchersRequest filterRequest) {
        final String mitmAddr = req.getRemoteAddr();
        if (filterRequest == null || !filterRequest.hasRequestId() || empty(requestId) || !requestId.equals(filterRequest.getRequestId())) {
            if (log.isDebugEnabled()) log.debug("selectMatchers: no filterRequest, missing requestId, or mismatch, returning forbidden");
            return forbidden();
        }
        // only mitmproxy is allowed to call us, and this should always be a local address
        if (!isLocalIpv4(mitmAddr)) {
            if (log.isDebugEnabled()) log.debug("selectMatchers: mitmAddr ("+mitmAddr+") was not local IPv4 for filterRequest ("+filterRequest.getRequestId()+"), returning forbidden");
            return forbidden();
        }

        final String prefix = "selectMatchers("+filterRequest.getRequestId()+"): ";
        if (log.isDebugEnabled()) log.debug(prefix+"starting for filterRequest="+json(filterRequest, COMPACT_MAPPER));

        if (!filterRequest.hasRemoteAddr()) {
            if (log.isDebugEnabled()) log.debug(prefix+"no VPN address provided, returning no matchers");
            return ok(NO_MATCHERS);
        }

        final String vpnAddr = filterRequest.getRemoteAddr();
        final Device device = deviceIdService.findDeviceByIp(vpnAddr);
        if (device == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"device not found for IP "+vpnAddr+", returning no matchers");
            return ok(NO_MATCHERS);
        } else if (log.isDebugEnabled()) {
            log.debug(prefix+"found device "+device.id()+" for IP "+vpnAddr);
        }
        filterRequest.setDevice(device.getUuid());
        final FilterMatchersResponse response = getMatchersResponse(filterRequest, req, request);
        if (log.isDebugEnabled()) log.debug(prefix+"returning response: "+json(response, COMPACT_MAPPER));
        return ok(response);
    }

    private FilterMatchersResponse findMatchers(FilterMatchersRequest filterRequest, Request req, ContainerRequest request) {

        final String prefix = "findMatchers("+filterRequest.getRequestId()+"): ";
        final Device device = findDevice(filterRequest.getDevice());
        if (device == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"findDevice("+ filterRequest.getDevice() +") returned null, returning no matchers");
            return NO_MATCHERS;
        }
        final String accountUuid = device.getAccount();
        final Account caller = findCaller(accountUuid);
        if (caller == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"account "+ accountUuid +" not found for device "+device.id()+", returning no matchers");
            return NO_MATCHERS;
        }

        final String fqdn = filterRequest.getFqdn();
        final List<AppMatcher> matchers = matcherDAO.findByAccountAndFqdnAndEnabled(accountUuid, fqdn);
        if (log.isDebugEnabled()) log.debug(prefix+"found "+matchers.size()+" candidate matchers");
        final Map<String, AppMatcher> retainMatchers;
        if (matchers.isEmpty()) {
            retainMatchers = emptyMap();
        } else {
            final String uri = filterRequest.getUri();
            retainMatchers = new HashMap<>();
            for (AppMatcher matcher : matchers) {
                if (retainMatchers.containsKey(matcher.getUuid())) continue;
                if (matcher.matches(uri)) {
                    if (log.isDebugEnabled()) log.debug(prefix+"matcher "+matcher.getName()+" with pattern "+matcher.getUrlRegex()+" found match for uri: '"+uri+"'");
                    final FilterMatchDecision matchResponse = ruleEngine.preprocess(filterRequest, req, request, caller, device, matcher);
                    switch (matchResponse) {
                        case abort_ok:        return FilterMatchersResponse.ABORT_OK;
                        case abort_not_found: return FilterMatchersResponse.ABORT_NOT_FOUND;
                        case no_match:        break;
                        case match:           retainMatchers.put(matcher.getUuid(), matcher); break;
                    }
                }
            }
        }

        if (log.isDebugEnabled()) log.debug(prefix+"after pre-processing, returning "+retainMatchers.size()+" matchers");
        return new FilterMatchersResponse()
                .setDecision(empty(matchers) ? FilterMatchDecision.no_match : FilterMatchDecision.match)
                .setRequest(filterRequest)
                .setMatchers(new ArrayList<>(retainMatchers.values()));
    }

    @POST @Path(EP_APPLY+"/{requestId}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response filterHttp(@Context Request req,
                               @Context ContainerRequest request,
                               @PathParam("requestId") String requestId,
                               @QueryParam("encoding") String contentEncoding,
                               @QueryParam("contentType") String contentType,
                               @QueryParam("last") Boolean last) throws IOException {

        // only mitmproxy is allowed to call us, and this should always be a local address
        final String mitmAddr = req.getRemoteAddr();
        if (!isLocalIpv4(mitmAddr)) {
            if (log.isDebugEnabled()) log.debug("filterHttp: mitmAddr ("+mitmAddr+") was not local IPv4, returning forbidden");
            return forbidden();
        }

        if (empty(requestId)) {
            if (log.isDebugEnabled()) log.debug("filterHttp: no requestId provided, returning passthru");
            return passthru(request);
        }
        final String prefix = "filterHttp("+requestId+"): ";

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
        Integer contentLength;
        try {
            contentLength = empty(contentLengthHeader) ? null : Integer.parseInt(contentLengthHeader);
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug(prefix+"error parsing Content-Length ("+contentLengthHeader+"): "+shortError(e));
            contentLength = null;
        }

        final FilterMatchersResponse matchersResponse = getMatchersResponseByRequestId(requestId);
        if (matchersResponse == null) {
            if (log.isWarnEnabled()) log.warn(prefix+"FilterMatchersResponse not found, returning passthru");
            return passthru(request);

        }
        if (log.isDebugEnabled()) log.debug(prefix+"found FilterMatchersResponse: "+json(matchersResponse, COMPACT_MAPPER));
        if (matchersResponse.hasAbort()) {
            if (log.isWarnEnabled()) log.warn(prefix+"FilterMatchersResponse has abort code "+matchersResponse.httpStatus()+", MITM should have aborted. We are aborting now.");
            return status(matchersResponse.httpStatus());

        } else if (!matchersResponse.hasMatchers()) {
            if (log.isWarnEnabled()) log.warn(prefix+"FilterMatchersResponse has no matchers, returning passthru");
            return passthru(request);

        } else if (!matchersResponse.hasRequest()) {
            if (log.isWarnEnabled()) log.warn(prefix + "FilterMatchersResponse has no request, returning passthru");
            return passthru(request);

        } else {
            final FilterMatchDecision decision = matchersResponse.getDecision();
            if (decision != FilterMatchDecision.match) {
                switch (decision) {
                    case no_match:
                        if (log.isWarnEnabled()) log.warn(prefix + "FilterMatchersResponse decision was not match: "+ decision +", returning passthru");
                        return passthru(request);
                    case abort_not_found:
                    case abort_ok:
                        if (log.isWarnEnabled()) log.warn(prefix + "FilterMatchersResponse decision was not match: "+ decision +", returning "+matchersResponse.httpStatus());
                        return status(matchersResponse.httpStatus());
                    default:
                        if (log.isWarnEnabled()) log.warn(prefix + "FilterMatchersResponse decision was unknown: "+ decision +", returning passthru");
                        return passthru(request);
                }

            } else if (!matchersResponse.getRequest().hasDevice()) {
                if (log.isWarnEnabled()) log.warn(prefix+"FilterMatchersResponse has no device, returning passthru");
                return passthru(request);
            }
        }
        matchersResponse.setRequestId(requestId);

        FilterHttpRequest filterRequest = getActiveRequest(requestId);
        if (filterRequest == null) {
            if (log.isDebugEnabled()) log.debug(prefix+"filterRequest not found, initiating...");
            if (empty(contentType)) {
                if (log.isDebugEnabled()) log.debug(prefix+"filter request not found, and no contentType provided, returning passthru");
                return passthru(request);
            }

            final Device device = findDevice(matchersResponse.getRequest().getDevice());
            if (device == null) {
                if (log.isDebugEnabled()) log.debug(prefix+"device "+matchersResponse.getRequest().getDevice()+" not found, returning passthru");
                return passthru(request);
            } else {
                if (log.isDebugEnabled()) log.debug(prefix+"found device: "+device.id()+" ... ");
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
                    .setContentType(contentType);
            if (log.isDebugEnabled()) log.trace(prefix+"start filterRequest="+json(filterRequest, COMPACT_MAPPER));
            getActiveRequestCache().set(requestId, json(filterRequest, COMPACT_MAPPER), EX, ACTIVE_REQUEST_TIMEOUT);
        } else {
            if (log.isDebugEnabled()) log.debug(prefix+"filterRequest found, continuing...");
            if (log.isTraceEnabled()) {
                if (isLast) {
                    log.trace(prefix+"last filterRequest=" + json(filterRequest, COMPACT_MAPPER));
                } else {
                    log.trace(prefix+"continuing filterRequest=" + json(filterRequest, COMPACT_MAPPER));
                }
            }
        }

        return ruleEngine.applyRulesToChunkAndSendResponse(request, filterRequest, contentLength, isLast);
    }

    public Response passthru(@Context ContainerRequest request) { return ruleEngine.passthru(request); }

    @GET @Path(EP_DATA+"/{requestId}/{matcherId}"+EP_READ)
    @Produces(APPLICATION_JSON)
    public Response readData(@Context Request req,
                             @Context ContainerRequest ctx,
                             @PathParam("requestId") String requestId,
                             @PathParam("matcherId") String matcherId,
                             @QueryParam("format") AppDataFormat format) {

        final FilterDataContext fdc = new FilterDataContext(req, requestId, matcherId);
        final List<AppData> data = dataDAO.findEnabledByAccountAndAppAndSite
                (fdc.request.getAccount().getUuid(), fdc.matcher.getApp(), fdc.matcher.getSite());

        if (log.isDebugEnabled()) log.debug("readData: found "+data.size()+" AppData records");

        if (format == null) format = AppDataFormat.key;
        switch (format) {
            case key:
                return ok(data.stream().map(AppData::getKey).collect(Collectors.toList()));
            case value:
                return ok(data.stream().map(AppData::getData).collect(Collectors.toList()));
            case key_value:
                return ok(data.stream().collect(Collectors.toMap(AppData::getKey, AppData::getData)));
            case full:
                return ok(data);
            default:
                throw notFoundEx(format.name());
        }
    }

    @POST @Path(EP_DATA+"/{requestId}/{matcherId}"+EP_WRITE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response writeData(@Context Request req,
                              @Context ContainerRequest ctx,
                              @PathParam("requestId") String requestId,
                              @PathParam("matcherId") String matcherId,
                              AppData data) {
        if (data == null || !data.hasKey()) throw invalidEx("err.key.required");
        return ok(writeData(req, requestId, matcherId, data));
    }

    @GET @Path(EP_DATA+"/{requestId}/{matcherId}"+EP_WRITE)
    @Produces(APPLICATION_JSON)
    public Response writeData(@Context Request req,
                              @Context ContainerRequest ctx,
                              @PathParam("requestId") String requestId,
                              @PathParam("matcherId") String matcherId,
                              @QueryParam(Q_DATA) String dataJson,
                              @QueryParam(Q_REDIRECT) String redirectLocation) {
        if (empty(dataJson)) throw invalidEx("err.data.required");
        final AppData data;
        try {
            data = json(dataJson, AppData.class);
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("writeData: invalid data="+dataJson+": "+shortError(e));
            throw invalidEx("err.data.invalid");
        }
        if (!data.hasKey()) throw invalidEx("err.key.required");

        final FilterDataContext fdc = writeData(req, requestId, matcherId, data);

        if (!empty(redirectLocation)) {
            if (redirectLocation.trim().equalsIgnoreCase(Boolean.FALSE.toString())) {
                return ok(data);
            } else {
                return redirect(redirectLocation);
            }
        } else {
            final String referer = req.getHeader("Referer");
            if (referer != null) return redirect(referer);
            return redirect(".");
        }
    }

    private FilterDataContext writeData(Request req, String requestId, String matcherId, AppData data) {
        if (log.isDebugEnabled()) log.debug("writeData: received data=" + json(data, COMPACT_MAPPER));
        final FilterDataContext fdc = new FilterDataContext(req, requestId, matcherId);

        data.setAccount(fdc.request.getAccount().getUuid());
        data.setDevice(fdc.request.getDevice().getUuid());
        data.setApp(fdc.matcher.getApp());
        data.setSite(fdc.matcher.getSite());
        data.setMatcher(fdc.matcher.getUuid());

        if (log.isDebugEnabled()) log.debug("writeData: recording data=" + json(data, COMPACT_MAPPER));
        fdc.data = dataDAO.set(data);
        return fdc;
    }

    private class FilterDataContext {
        public FilterHttpRequest request;
        public AppMatcher matcher;
        public AppData data;

        public FilterDataContext(Request req, String requestId, String matcherId) {
            // only mitmproxy is allowed to call us, and this should always be a local address
            final String mitmAddr = req.getRemoteAddr();
            if (!isLocalIpv4(mitmAddr)) throw forbiddenEx();

            if (empty(requestId) || empty(matcherId)) throw notFoundEx();

            request = getActiveRequest(requestId);
            if (request == null) throw notFoundEx(requestId);
            if (!request.hasMatcher(matcherId)) throw notFoundEx(matcherId);

            matcher = matcherDAO.findByAccountAndId(request.getAccount().getUuid(), matcherId);
            if (matcher == null) throw notFoundEx(matcherId);
        }
    }
}