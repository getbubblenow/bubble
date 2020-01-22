package bubble.resources.stream;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.AppMatcherDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.device.Device;
import bubble.service.cloud.DeviceIdService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.ExpirationMap;
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

import static bubble.ApiConstants.*;
import static bubble.resources.stream.FilterMatchersResponse.NO_MATCHERS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.isLocalIpv4;
import static org.cobbzilla.wizard.resources.ResourceUtil.forbidden;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;

@Path(FILTER_HTTP_ENDPOINT)
@Service @Slf4j
public class FilterHttpResource {

    @Autowired private AccountDAO accountDAO;
    @Autowired private RuleEngine ruleEngine;
    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private DeviceDAO deviceDAO;
    @Autowired private DeviceIdService deviceIdService;

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

        if (log.isDebugEnabled()) {
            log.debug("filterHttp: starting with requestId="+requestId+", deviceId="+deviceId+", matchersJson="+matchersJson+", contentType="+contentType+", last="+last);

            // for now, just try to return unmodified...
//            if (last != null && last) {
//                log.debug("filterHttp: DEBUG: last chunk detected, returning empty response");
//                return ok(); // no response body
//            } else {
//                log.debug("filterHttp: DEBUG: chunk detected, returning chunk as passthru response");
//                return passthru(request);
//            }
        }

        final boolean isLast = last != null && last;
        return ruleEngine.applyRulesToChunkAndSendResponse(
                request, filterRequest.getId(), filterRequest.getAccount(), filterRequest.getDevice(), filterRequest.getMatchers(),
                isLast);
    }

    public Response passthru(@Context ContainerRequest request) { return ruleEngine.passthru(request); }

}
