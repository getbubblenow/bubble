/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream;

import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.RuleDriverDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.RuleDriver;
import bubble.model.device.Device;
import bubble.resources.stream.FilterHttpRequest;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.AppRuleDriver;
import bubble.rule.FilterMatchDecision;
import bubble.server.BubbleConfiguration;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.cobbzilla.util.collection.ExpirationEvictionPolicy;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.http.HttpClosingFilterInputStream;
import org.cobbzilla.util.http.HttpMethods;
import org.cobbzilla.util.http.URIBean;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.stream.ByteStreamingOutput;
import org.cobbzilla.wizard.stream.SendableResource;
import org.cobbzilla.wizard.stream.StreamStreamingOutput;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.Response;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static bubble.client.BubbleApiClient.newHttpClientBuilder;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpHeaders.TRANSFER_ENCODING;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;
import static org.cobbzilla.util.http.HttpStatusCodes.OK;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.wizard.cache.redis.RedisService.ALL_KEYS;
import static org.cobbzilla.wizard.resources.ResourceUtil.send;

@Service @Slf4j
public class StandardRuleEngineService implements RuleEngineService {

    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private RuleDriverDAO driverDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private RedisService redis;

    public static final long MATCHERS_CACHE_TIMEOUT = MINUTES.toSeconds(15);
    @Getter(lazy=true) private final RedisService matchersCache = redis.prefixNamespace(getClass().getSimpleName()+".matchers");

    public FilterMatchDecision preprocess(FilterMatchersRequest filter,
                                          Request req,
                                          ContainerRequest request,
                                          Account account,
                                          Device device,
                                          AppMatcher matcher) {
        final AppRuleHarness ruleHarness = initRules(account, device, new SingletonList<AppMatcher>(matcher)).get(0);
        return ruleHarness.getDriver().preprocess(ruleHarness, filter, account, device, req, request);
    }

    public Response passthru(URIBean ub, ContainerRequest request) throws IOException {

        final HttpGet proxyRequest = new HttpGet(ub.toURI());

        // todo: copy other request headers too? all request headers?
        proxyRequest.setHeader("User-Agent", request.getHeaderString("User-Agent"));

        final CloseableHttpClient httpClient = newHttpConn();
        @Cleanup final CloseableHttpResponse proxyResponse = httpClient.execute(proxyRequest);

        final StatusLine statusLine = proxyResponse.getStatusLine();
        @Cleanup InputStream content = proxyResponse.getEntity().getContent();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        IOUtils.copy(content, data);

        final SendableResource response = new SendableResource(new ByteStreamingOutput(data.toByteArray()))
                .setStatus(statusLine.getStatusCode())
                .setStatusReason(statusLine.getReasonPhrase());
        for (Header h : proxyResponse.getAllHeaders()) {
            response.addHeader(h.getName(), h.getValue());
        }
        return send(response);
    }

    public Response applyRulesAndSendResponse(ContainerRequest request,
                                              URIBean ub,
                                              FilterHttpRequest filterRequest) throws IOException {

        // sanity check
        if (empty(filterRequest.getMatchers())) return passthru(request.getEntityStream());

        final List<AppRuleHarness> rules = initRules(filterRequest);
        final AppRuleHarness firstRule = rules.get(0);

        // filter request
        final HttpGet get = initHttpClientProxyRequest(ub, request, firstRule);

        // exec http
        final CloseableHttpClient httpClient = newHttpConn();
        final CloseableHttpResponse proxyResponse = httpClient.execute(get);

        // filter response. when stream is closed, close http client
        final Header contentTypeHeader = proxyResponse.getFirstHeader(CONTENT_TYPE);
        filterRequest.setContentType(contentTypeHeader == null ? null : contentTypeHeader.getValue());
        final InputStream responseEntity = firstRule.getDriver().filterResponse(filterRequest, new HttpClosingFilterInputStream(httpClient, proxyResponse));

        // send response
        return sendResponse(responseEntity, proxyResponse);
    }

    public Response passthru(InputStream stream) {
        final SendableResource response = new SendableResource(new StreamStreamingOutput(stream))
                .setStatus(OK);
        return send(response);
    }

    public Response passthru(ContainerRequest request) { return passthru(request.getEntityStream()); }

    private final Map<String, ActiveStreamState> activeProcessors = new ExpirationMap<>(MINUTES.toMillis(5), ExpirationEvictionPolicy.atime);

    private static final boolean DEBUG_CAPTURE = false;

    public Response applyRulesToChunkAndSendResponse(ContainerRequest request,
                                                     FilterHttpRequest filterRequest,
                                                     Integer chunkLength,
                                                     boolean last) throws IOException {
        final String prefix = "applyRulesToChunkAndSendResponse("+filterRequest.getId()+"): ";
        if (!filterRequest.hasMatchers()) {
            if (log.isDebugEnabled()) log.debug(prefix+"adding no matchers, returning passthru");
            return passthru(request);
        } else {
            log.info(prefix+" applying ");
        }

        // for debugging problematic requests
        if (DEBUG_CAPTURE) {
            final byte[] bytes = IOUtils.toByteArray(request.getEntityStream());
            final File temp = new File("/tmp/"+filterRequest.getId()+".raw");
            try (FileOutputStream out = new FileOutputStream(temp, true)) {
                log.debug(prefix+"stashed "+bytes.length+" bytes in "+abs(temp));
                IOUtils.copy(new ByteArrayInputStream(bytes), out);
            }
            return sendResponse(new ByteArrayInputStream(bytes)); // noop for testing
        }

        // have we seen this request before?
        final ActiveStreamState state = activeProcessors.computeIfAbsent(filterRequest.getId(),
                k -> new ActiveStreamState(filterRequest, initRules(filterRequest)));
        if (state.isPassthru()) {
            if (log.isDebugEnabled()) log.debug(prefix+"state is passthru");
            return passthru(request);
        }
        if (last) {
            if (log.isDebugEnabled()) log.debug(prefix+"adding LAST stream with length="+chunkLength);
            state.addLastChunk(request.getEntityStream(), chunkLength);
        } else {
            if (log.isDebugEnabled()) log.debug(prefix+"adding a stream with length="+chunkLength);
            state.addChunk(request.getEntityStream(), chunkLength);
        }

        if (log.isDebugEnabled()) log.debug(prefix+"sending as much filtered data as we can right now (which may be nothing)");
        return sendResponse(state.getResponseStream(last));
    }

    private final ExpirationMap<String, List<AppRuleHarness>> ruleCache
            = new ExpirationMap<>(HOURS.toMillis(1), ExpirationEvictionPolicy.atime);

    public Map<Object, Object> flushCaches() {
        final int ruleEngineCacheSize = ruleCache.size();
        ruleCache.clear();
        if (log.isInfoEnabled()) log.info("flushCaches: flushed "+ruleEngineCacheSize+" ruleCache entries");

        final RedisService matchersCache = getMatchersCache();
        final Long matcherCount = matchersCache.del_matching(ALL_KEYS);
        if (log.isInfoEnabled()) log.info("flushCaches: flushed "+matcherCount+" matchersCache entries");

        final Long connCheckDeletions = redis.del_matching("bubble_conn_check_*");
        if (log.isInfoEnabled()) log.info("flushCaches: removed "+connCheckDeletions+" conn_check cache entries");

        return MapBuilder.build(new Object[][] {
                {"connCheckCache", connCheckDeletions},
                {"matchersCache", matcherCount},
                {"ruleEngineCache", ruleEngineCacheSize}
        });
    }

    private List<AppRuleHarness> initRules(FilterHttpRequest filterRequest) {
        return initRules(filterRequest.getAccount(), filterRequest.getDevice(), filterRequest.getMatchers());
    }

    private List<AppRuleHarness> initRules(Account account, Device device, List<AppMatcher> matchers) {
        if (empty(matchers)) return emptyList();
        final String cacheKey = hashOf(account.getUuid(), device.getUuid(), matchers);
        return ruleCache.computeIfAbsent(cacheKey, k -> {
            final List<AppRuleHarness> ruleHarnesses = new ArrayList<>();
            for (AppMatcher m : matchers) {
                final AppRule rule = ruleDAO.findByUuid(m.getRule());
                if (rule == null) {
                    log.warn("initRules: rule not found ("+m.getRule()+") for matcher: "+m.getUuid());
                    continue;
                }
                ruleHarnesses.add(new AppRuleHarness(m, rule));
            }
            return initRuleHarnesses(account, device, ruleHarnesses);
        });
    }

    private List<AppRuleHarness> initRuleHarnesses(Account account, Device device, List<AppRuleHarness> rules) {
        for (AppRuleHarness h : rules) {
            final RuleDriver ruleDriver = driverDAO.findByUuid(h.getRule().getDriver());
            if (ruleDriver == null) {
                log.warn("initRules: driver not found: "+h.getRule().getDriver());
                continue;
            }
            final AppRuleDriver unwiredDriver = h.getRule().initDriver(ruleDriver, h.getMatcher(), account, device);
            final AppRuleDriver driver = configuration.autowire(unwiredDriver);
            h.setRuleDriver(ruleDriver);
            h.setDriver(driver);
        }
        for (int i=0; i<rules.size()-1; i++) {
            rules.get(i).getDriver().setNext(rules.get(i+1).getDriver());
        }
        return rules;
    }

    public Response sendResponse(InputStream stream) { return sendResponse(stream, null); }

    public Response sendResponse(InputStream stream, CloseableHttpResponse proxyResponse) {
        final SendableResource actualResponse = new SendableResource(new StreamStreamingOutput(stream))
                .setStatus(proxyResponse == null ? OK : proxyResponse.getStatusLine().getStatusCode());

        if (proxyResponse != null) {
            boolean foundTransferEncoding = false;
            for (Header h : proxyResponse.getAllHeaders()) {
                // do not copy Content-Length header, since we will be using chunked transfer encoding
                if (h.getName().equals(TRANSFER_ENCODING)) {
                    foundTransferEncoding = true;
                } else if (h.getName().equals(CONTENT_LENGTH)) {
                    continue;
                }
                actualResponse.addHeader(h.getName(), h.getValue());
            }
            if (!foundTransferEncoding) actualResponse.addHeader(TRANSFER_ENCODING, "chunked");
        }

        return send(actualResponse);
    }

    private <T extends HttpRequestBase> T initHttpClientProxyRequest(URIBean ub, ContainerRequest request, AppRuleHarness firstRule) {
        final T proxyRequest = (T) HttpMethods.request(request.getMethod(), ub.toURI().toString());

        for (Map.Entry<String, List<String>> e : request.getRequestHeaders().entrySet()) {
            for (String value : e.getValue()) {
                proxyRequest.setHeader(e.getKey(), value);
            }
        }
        if (proxyRequest instanceof HttpEntityEnclosingRequestBase) {
            ((HttpEntityEnclosingRequestBase) proxyRequest).setEntity(new InputStreamEntity(firstRule.getDriver().filterRequest(request.getEntityStream())));
        }
        return proxyRequest;
    }


    @Getter(lazy=true) private final HttpClientBuilder httpClientBuilder = newHttpClientBuilder(1000, 50);
    public CloseableHttpClient newHttpConn() { return getHttpClientBuilder().build(); }

    public ConnectionCheckResponse checkConnection(Account account, Device device, List<AppMatcher> matchers, String addr, String fqdn) {
        final List<AppRuleHarness> ruleHarnesses = initRules(account, device, matchers);
        for (AppRuleHarness harness : ruleHarnesses) {
            final ConnectionCheckResponse checkResponse = harness.getDriver().checkConnection(harness, account, device, addr, fqdn);
            if (checkResponse != ConnectionCheckResponse.noop) return checkResponse;
        }
        return ConnectionCheckResponse.noop;
    }

}
