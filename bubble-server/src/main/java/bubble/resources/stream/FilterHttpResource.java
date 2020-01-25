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
import bubble.service.cloud.DeviceIdService;
import bubble.service.stream.RuleEngine;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.http.HttpContentEncodingType;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static bubble.ApiConstants.*;
import static bubble.resources.stream.FilterMatchersResponse.NO_MATCHERS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.isLocalIpv4;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Path(FILTER_HTTP_ENDPOINT)
@Service @Slf4j
public class FilterHttpResource {

    @Autowired private AccountDAO accountDAO;
    @Autowired private RuleEngine ruleEngine;
    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private DeviceDAO deviceDAO;
    @Autowired private DeviceIdService deviceIdService;
    @Autowired private AppDataDAO dataDAO;

    private Map<String, Account> accountCache = new ExpirationMap<>(MINUTES.toMillis(10));
    public Account findCaller(String accountUuid) {
        return accountCache.computeIfAbsent(accountUuid, uuid -> accountDAO.findByUuid(uuid));
    }

    private Map<String, Device> deviceCache = new ExpirationMap<>(MINUTES.toMillis(10));
    public Device findDevice(String deviceUuid) {
        return deviceCache.computeIfAbsent(deviceUuid, uuid -> deviceDAO.findByUuid(uuid));
    }

    private Map<String, FilterMatchersResponse> matchersCache = new ExpirationMap<>(MINUTES.toMillis(5));

    @POST @Path(EP_MATCHERS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response matchersForFqdn(@Context Request req,
                                    @Context ContainerRequest request,
                                    FilterMatchersRequest filterRequest) {
        final String remoteHost = getRemoteHost(req);
        final String mitmAddr = req.getRemoteAddr();
        if (log.isDebugEnabled()) log.debug("matchersForFqdn: starting for remoteHost=" + remoteHost + ", mitmAddr=" + mitmAddr+", filterRequest="+json(filterRequest));

        // only mitmproxy is allowed to call us, and this should always be a local address
        if (!isLocalIpv4(mitmAddr)) return forbidden();

        final String cacheKey = remoteHost+":"+filterRequest.cacheKey();
        final FilterMatchersResponse response = matchersCache.computeIfAbsent(cacheKey, k -> findMatchers(filterRequest, req, request));

        return ok(response);
    }

    private FilterMatchersResponse findMatchers(FilterMatchersRequest filterRequest, Request req, ContainerRequest request) {
        final String vpnAddr = filterRequest.getRemoteAddr();
        if (empty(vpnAddr)) {
            if (log.isDebugEnabled()) log.debug("findMatchers: no VPN address provided, returning no matchers");
            return NO_MATCHERS;
        }

        final Device device = deviceIdService.findDeviceByIp(vpnAddr);
        if (device == null) {
            if (log.isDebugEnabled()) log.debug("findMatchers: device not found for IP "+vpnAddr+", returning no matchers");
            return NO_MATCHERS;
        } else if (log.isDebugEnabled()) {
            log.debug("findMatchers: found device "+device.id()+" for IP "+vpnAddr);
        }

        final String accountUuid = device.getAccount();
        final Account caller = findCaller(accountUuid);
        if (caller == null) {
            if (log.isDebugEnabled()) log.debug("findMatchers: account "+ accountUuid +" not found for device "+device.id()+", returning no matchers");
            return NO_MATCHERS;
        }

        final String fqdn = filterRequest.getFqdn();
        final List<AppMatcher> matchers = matcherDAO.findByAccountAndFqdnAndEnabled(accountUuid, fqdn);
        if (log.isDebugEnabled()) log.debug("findMatchers: found "+matchers.size()+" candidate matchers");
        final List<AppMatcher> removeMatchers;
        if (matchers.isEmpty()) {
            removeMatchers = Collections.emptyList();
        } else {
            final String uri = filterRequest.getUri();
            removeMatchers = new ArrayList<>();
            for (AppMatcher matcher : matchers) {
                if (matcher.matches(uri)) {
                    if (ruleEngine.preprocess(filterRequest, req, request, caller, device, matcher.getUuid())) {
                        removeMatchers.add(matcher);
                    }
                }
            }
        }
        matchers.removeAll(removeMatchers);

        if (log.isDebugEnabled()) log.debug("findMatchers: after pre-processing, returning "+matchers.size()+" matchers");
        return new FilterMatchersResponse().setMatchers(matchers).setDevice(device.getUuid());
    }

    private Map<String, FilterHttpRequest> activeRequests = new ExpirationMap<>(MINUTES.toMillis(10));

    @POST @Path(EP_APPLY+"/{requestId}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response filterHttp(@Context Request req,
                               @Context ContainerRequest request,
                               @PathParam("requestId") String requestId,
                               @QueryParam("device") String deviceId,
                               @QueryParam("matchers") String matchersJson,
                               @QueryParam("encoding") String contentEncoding,
                               @QueryParam("contentType") String contentType,
                               @QueryParam("last") Boolean last) throws IOException {

        final String remoteHost = getRemoteHost(req);
        final String mitmAddr = req.getRemoteAddr();

        // only mitmproxy is allowed to call us, and this should always be a local address
        if (!isLocalIpv4(mitmAddr)) return forbidden();

        if (empty(requestId)) {
            if (log.isDebugEnabled()) log.debug("filterHttp: no requestId provided, returning passthru");
            return passthru(request);
        }

        // can we handle the encoding?
        final HttpContentEncodingType encoding;
        if (empty(contentEncoding)) {
            encoding = null;
        } else {
            try {
                encoding = HttpContentEncodingType.fromString(contentEncoding);
            } catch (Exception e) {
                if (log.isWarnEnabled()) log.warn("filterHttp: invalid encoding ("+contentEncoding+"), returning passthru");
                return passthru(request);
            }
        }

        // mitmproxy provides Content-Length, which helps us right-size the input byte buffer
        final String contentLengthHeader = req.getHeader(CONTENT_LENGTH);
        Integer contentLength;
        try {
            contentLength = empty(contentLengthHeader) ? null : Integer.parseInt(contentLengthHeader);
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("filterHttp: error parsing Content-Length ("+contentLengthHeader+"): "+shortError(e));
            contentLength = null;
        }

        FilterHttpRequest filterRequest = activeRequests.get(requestId);
        if (filterRequest == null) {
            if (empty(deviceId) || empty(matchersJson) || empty(contentType)) {
                if (log.isDebugEnabled()) log.debug("filterHttp: filter request not found, and no device/matchers/contentType provided, returning passthru");
                return passthru(request);
            }
            final String[] matchers;
            try {
                matchers = json(matchersJson, String[].class);
            } catch (Exception e) {
                if (log.isDebugEnabled()) log.debug("filterHttp: error parsing matchers ("+matchersJson+"), returning passthru");
                return passthru(request);
            }
            if (matchers.length == 0) {
                if (log.isDebugEnabled()) log.debug("filterHttp: empty matchers array, returning passthru");
                return passthru(request);
            } else {
                if (log.isDebugEnabled()) log.debug("filterHttp: found matchers: "+ArrayUtil.arrayToString(matchers)+" ... ");
            }
            final Device device = findDevice(deviceId);
            if (device == null) {
                if (log.isDebugEnabled()) log.debug("filterHttp: device "+deviceId+" not found, returning passthru");
                return passthru(request);
            } else {
                if (log.isDebugEnabled()) log.debug("filterHttp: found device: "+device.id()+" ... ");
            }
            final Account caller = findCaller(device.getAccount());
            if (caller == null) {
                if (log.isDebugEnabled()) log.debug("filterHttp: account "+device.getAccount()+" not found, returning passthru");
                return passthru(request);
            }
            filterRequest = new FilterHttpRequest()
                    .setId(requestId)
                    .setDevice(device)
                    .setAccount(caller)
                    .setMatchers(matchers)
                    .setContentType(contentType);
            activeRequests.put(requestId, filterRequest);
        }

        if (log.isTraceEnabled()) {
            log.trace("filterHttp: starting with requestId="+requestId+", deviceId="+deviceId+", matchersJson="+matchersJson+", contentType="+contentType+", last="+last);
        }

        final boolean isLast = last != null && last;
        return ruleEngine.applyRulesToChunkAndSendResponse(request, encoding, contentLength,
                        filterRequest.getId(), filterRequest.getAccount(), filterRequest.getDevice(),
                        filterRequest.getMatchers(), isLast);
    }

    public Response passthru(@Context ContainerRequest request) { return ruleEngine.passthru(request); }

    @GET @Path(EP_DATA+"/{requestId}/{matcherId}"+EP_READ)
    @Produces(APPLICATION_JSON)
    public Response readData(@Context ContainerRequest ctx,
                             @PathParam("requestId") String requestId,
                             @PathParam("matcherId") String matcherId,
                             @QueryParam("format") AppDataFormat format) {

        final FilterDataContext fdc = new FilterDataContext(requestId, matcherId);
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
    public Response writeData(@Context ContainerRequest ctx,
                              @PathParam("requestId") String requestId,
                              @PathParam("matcherId") String matcherId,
                              AppData data) {

        if (data == null || !data.hasKey()) throw invalidEx("err.key.required");
        if (log.isDebugEnabled()) log.debug("writeData: received data="+json(data, COMPACT_MAPPER));
        final FilterDataContext fdc = new FilterDataContext(requestId, matcherId);

        data.setAccount(fdc.request.getAccount().getUuid());
        data.setApp(fdc.matcher.getApp());
        data.setSite(fdc.matcher.getSite());
        data.setMatcher(fdc.matcher.getUuid());

        if (log.isDebugEnabled()) log.debug("writeData: recording data="+json(data, COMPACT_MAPPER));
        return ok(dataDAO.create(data));
    }

    private class FilterDataContext {
        public FilterHttpRequest request;
        public AppMatcher matcher;

        public FilterDataContext(String requestId, String matcherId) {
            if (empty(requestId) || empty(matcherId)) throw notFoundEx();

            request = activeRequests.get(requestId);
            if (request == null) throw notFoundEx(requestId);
            if (!request.hasMatcher(matcherId)) throw notFoundEx(matcherId);

            matcher = matcherDAO.findByAccountAndId(request.getAccount().getUuid(), matcherId);
            if (matcher == null) throw notFoundEx(matcherId);
        }
    }
}