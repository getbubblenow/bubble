package bubble.resources.stream;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.AppMatcherDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.device.Device;
import bubble.service.cloud.DeviceIdService;
import lombok.extern.slf4j.Slf4j;
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
                    if (ruleEngine.preprocess(filterRequest, req, request, caller, matcher.getUuid())) {
                        removeMatchers.add(matcher);
                    }
                }
            }
        }
        matchers.removeAll(removeMatchers);

        if (log.isDebugEnabled()) log.debug("findMatchers: after pre-processing, returning "+matchers.size()+" matchers");
        return new FilterMatchersResponse().setMatchers(matchers).setDevice(device.getUuid());
    }

    @POST @Path(EP_APPLY+"/{matchers}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response filterHttp(@Context Request req,
                               @Context ContainerRequest request,
                               @PathParam("matchers") String matchersJson,
                               @QueryParam("device") String deviceId) throws IOException {
        final String remoteHost = getRemoteHost(req);
        final String mitmAddr = req.getRemoteAddr();

        // only mitmproxy is allowed to call us, and this should always be a local address
        if (!isLocalIpv4(mitmAddr)) return forbidden();

        if (missing(matchersJson)) {
            log.info("filterHttp: no matchers provided, returning passthru");
            return passthru(request);
        }
        if (empty(deviceId)) {
            log.info("filterHttp: no deviceId provided, returning passthru");
            return passthru(request);
        }

        final String[] matchers;
        try {
            matchers = json(matchersJson, String[].class);
        } catch (Exception e) {
            log.info("filterHttp: error parsing matchers ("+matchersJson+"), returning passthru");
            return passthru(request);
        }
        if (matchers.length == 0) {
            log.info("filterHttp: empty matchers array, returning passthru");
            return passthru(request);
        }

        final Device device = findDevice(deviceId);
        if (device == null) {
            log.info("filterHttp: device "+deviceId+" not found, returning passthru");
            return passthru(request);
        }

        final Account caller = findCaller(device.getAccount());
        if (caller == null) {
            log.info("filterHttp: account "+device.getAccount()+" not found, returning passthru");
            return passthru(request);
        }

        return ruleEngine.applyRulesAndSendResponse(request, caller, matchers);
    }

    private boolean missing(String v) { return v == null || v.trim().length() == 0; }

    public Response passthru(@Context ContainerRequest request) { return ruleEngine.passthru(request); }

}
