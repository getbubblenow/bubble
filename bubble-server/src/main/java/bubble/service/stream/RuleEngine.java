package bubble.service.stream;

import bubble.dao.app.AppMatcherDAO;
import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.RuleDriverDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.RuleDriver;
import bubble.model.device.Device;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.rule.AppRuleDriver;
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
import org.cobbzilla.util.http.HttpClosingFilterInputStream;
import org.cobbzilla.util.http.HttpMethods;
import org.cobbzilla.util.http.URIBean;
import org.cobbzilla.util.io.NullInputStream;
import org.cobbzilla.util.io.multi.MultiStream;
import org.cobbzilla.wizard.stream.ByteStreamingOutput;
import org.cobbzilla.wizard.stream.SendableResource;
import org.cobbzilla.wizard.stream.StreamStreamingOutput;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static bubble.client.BubbleApiClient.newHttpClientBuilder;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;
import static org.cobbzilla.util.http.HttpStatusCodes.OK;
import static org.cobbzilla.util.io.StreamUtil.DEFAULT_BUFFER_SIZE;
import static org.cobbzilla.wizard.resources.ResourceUtil.send;

@Service @Slf4j
public class RuleEngine {

    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private RuleDriverDAO driverDAO;
    @Autowired private BubbleConfiguration configuration;

    public boolean preprocess(FilterMatchersRequest filter,
                              Request req,
                              ContainerRequest request,
                              Account account,
                              Device device,
                              String matcherUuid) {
        final AppRuleHarness ruleHarness = initRules(account, device, new String[]{ matcherUuid }).get(0);
        return ruleHarness.getDriver().preprocess(ruleHarness, filter, account, req, request);
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
                                              Account account,
                                              Device device,
                                              URIBean ub,
                                              String[] matcherIds) throws IOException {

        // sanity check
        if (empty(matcherIds)) return passthru(request.getEntityStream());

        // todo: we have at least 1 rule, so add another rule that inserts the global settings controls in the top-left

        // initialize drivers -- todo: cache drivers / todo: ensure cache is shorter than session timeout,
        // since drivers that talk thru API will get a session key in their config
        final List<AppRuleHarness> rules = initRules(account, device, matcherIds);
        final AppRuleHarness firstRule = rules.get(0);

        // filter request
        final HttpGet get = initHttpClientProxyRequest(ub, request, firstRule);

        // exec http
        final CloseableHttpClient httpClient = newHttpConn();
        final CloseableHttpResponse proxyResponse = httpClient.execute(get);

        // filter response. when stream is closed, close http client
        final String requestId = randomUUID().toString();
        final InputStream responseEntity = firstRule.getDriver().filterResponse(requestId, new HttpClosingFilterInputStream(httpClient, proxyResponse));

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

    public Response applyRulesToChunkAndSendResponse(ContainerRequest request,
                                                     String requestId,
                                                     Account account,
                                                     Device device,
                                                     String[] matcherIds,
                                                     boolean last) throws IOException {

        if (empty(matcherIds)) return passthru(request);

        // have we seen this request before?
        final ActiveStreamState state = activeProcessors.computeIfAbsent(requestId, k -> new ActiveStreamState(k, initRules(account, device, matcherIds)));
        final byte[] chunk = toBytes(request.getEntityStream());
        if (last) {
            if (log.isDebugEnabled()) log.debug("applyRulesToChunkAndSendResponse: adding LAST stream");
            state.addLastChunk(chunk);
        } else {
            if (log.isDebugEnabled()) log.debug("applyRulesToChunkAndSendResponse: adding a stream");
            state.addChunk(chunk);
        }

        if (log.isDebugEnabled()) log.debug("applyRulesToChunkAndSendResponse: sending as much filtered data as we can right now (which may be nothing)");
//        return sendResponse(new ByteArrayInputStream(chunk)); // noop for testing
        return sendResponse(state.getResponseStream(last));
    }

    public byte[] toBytes(InputStream entityStream) throws IOException {
        if (entityStream == null) return EMPTY_BYTE_ARRAY;
        final ByteArrayOutputStream bout = new ByteArrayOutputStream(8192);
        IOUtils.copyLarge(entityStream, bout);
        return bout.toByteArray();
    }

    private ExpirationMap<String, List<AppRuleHarness>> ruleCache = new ExpirationMap<>(HOURS.toMillis(1), ExpirationEvictionPolicy.atime);

    private List<AppRuleHarness> initRules(Account account, Device device, String[] matcherIds) {
        final String cacheKey = hashOf(account.getUuid(), device.getUuid(), matcherIds);
        return ruleCache.computeIfAbsent(cacheKey, k -> {
            final List<AppMatcher> matchers = matcherDAO.findByUuids(matcherIds);
            if (matchers.size() != matcherIds.length) {
                log.warn("initRules: duplicate rules, or could not resolve some rule(s)");
            }
            final List<AppRuleHarness> ruleHarnesses = new ArrayList<>();
            for (AppMatcher m : matchers) {
                final AppRule rule = ruleDAO.findByUuid(m.getRule());
                if (rule == null) {
                    log.warn("initRules: rule not found ("+m.getRule()+") for matcher: "+m.getUuid());
                    continue;
                }
                ruleHarnesses.add(new AppRuleHarness(m, rule));
            }
            return initRules(account, device, ruleHarnesses);
        });
    }

    private List<AppRuleHarness> initRules(Account account, Device device, List<AppRuleHarness> rules) {
        for (AppRuleHarness h : rules) {
            final RuleDriver ruleDriver = driverDAO.findByUuid(h.getRule().getDriver());
            if (ruleDriver == null) {
                log.warn("get: driver not found: "+h.getRule().getDriver());
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
            for (Header h : proxyResponse.getAllHeaders()) {
                // do not copy Content-Length header, since we will be chunking
                if (h.getName().equals(CONTENT_LENGTH)) continue;
                actualResponse.addHeader(h.getName(), h.getValue());
            }
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

    private static class ActiveStreamState {

        private String requestId;
        private MultiStream multiStream;
        private AppRuleHarness firstRule;
        private InputStream output = null;
        private long totalBytesWritten = 0;
        private long totalBytesRead = 0;

        public ActiveStreamState(String requestId, List<AppRuleHarness> rules) {
            this.requestId = requestId;
            this.firstRule = rules.get(0);
        }

        public void addChunk(byte[] chunk) {
            if (log.isDebugEnabled()) log.debug("addChunk: adding "+chunk.length+" bytes");
            totalBytesWritten += chunk.length;
            if (multiStream == null) {
                multiStream = new MultiStream(new ByteArrayInputStream(chunk));
                output = firstRule.getDriver().filterResponse(requestId, multiStream);
            } else {
                multiStream.addStream(new ByteArrayInputStream(chunk));
            }
        }

        public void addLastChunk(byte[] chunk) {
            if (log.isDebugEnabled()) log.debug("addLastChunk: adding "+chunk.length+" bytes");
            totalBytesWritten += chunk.length;
            if (multiStream == null) {
                multiStream = new MultiStream(new ByteArrayInputStream(chunk), true);
                output = firstRule.getDriver().filterResponse(requestId, multiStream);
            } else {
                multiStream.addLastStream(new ByteArrayInputStream(chunk));
            }
        }

        public InputStream getResponseStream(boolean last) throws IOException {
            if (log.isDebugEnabled()) log.debug("getResponseStream: starting with last="+last+", totalBytesWritten="+totalBytesWritten+", totalBytesRead="+totalBytesRead);
            // read to end of all streams, there is no more data coming in
            if (last) {
                if (log.isDebugEnabled()) log.debug("getResponseStream: last==true, returning full output");
                return output;
            }

            // try to read as many bytes as we have written, and have not yet read, less a safety buffer
            final int bytesToRead = (int) (totalBytesWritten - totalBytesRead - (4*DEFAULT_BUFFER_SIZE));
            if (bytesToRead < 0) {
                // we shouldn't try to read yet, less than 1024 bytes have been written
                if (log.isDebugEnabled()) log.debug("getResponseStream: not enough data written (bytesToRead="+bytesToRead+"), can't read anything yet");
                return NullInputStream.instance;
            }

            if (log.isDebugEnabled()) log.debug("getResponseStream: trying to read "+bytesToRead+" bytes");
            final byte[] buffer = new byte[bytesToRead];
            final int bytesRead = output.read(buffer);
            if (log.isDebugEnabled()) log.debug("getResponseStream: actually read "+bytesRead+" bytes");
            if (bytesRead == -1) {
                // nothing to return
                if (log.isDebugEnabled()) log.debug("getResponseStream: end of stream, returning NullInputStream");
                return NullInputStream.instance;
            }

            if (log.isDebugEnabled()) log.debug("getResponseStream: read "+bytesRead+", returning buffer");
            totalBytesRead += bytesRead;
            return new ByteArrayInputStream(buffer, 0, bytesRead);
        }
    }

}
