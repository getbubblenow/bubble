package bubble.resources.stream;

import bubble.dao.app.AppMatcherDAO;
import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.RuleDriverDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.RuleDriver;
import bubble.model.device.Device;
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
import org.cobbzilla.util.http.HttpClosingFilterInputStream;
import org.cobbzilla.util.http.HttpMethods;
import org.cobbzilla.util.http.URIBean;
import org.cobbzilla.wizard.stream.ByteStreamingOutput;
import org.cobbzilla.wizard.stream.SendableResource;
import org.cobbzilla.wizard.stream.StreamStreamingOutput;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static bubble.client.BubbleApiClient.newHttpClientBuilder;
import static javax.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpStatusCodes.OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.send;
import static org.cobbzilla.wizard.util.SpringUtil.autowire;

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
                                              List<AppRuleHarness> rules) throws IOException {

        // sanity check
        if (rules == null || rules.isEmpty()) return passthru(request.getEntityStream());

        // todo: we have at least 1 rule, so add another rule that inserts the global settings controls in the top-left

        // initialize drivers -- todo: cache drivers / todo: ensure cache is shorter than session timeout,
        // since drivers that talk thru API will get a session key in their config
        rules = initRules(account, device, rules);
        final AppRuleHarness firstRule = rules.get(0);

        // filter request
        final HttpGet get = initHttpClientProxyRequest(ub, request, firstRule);

        // exec http
        final CloseableHttpClient httpClient = newHttpConn();
        final CloseableHttpResponse proxyResponse = httpClient.execute(get);

        // filter response. when stream is closed, close http client
        final InputStream responseEntity = firstRule.getDriver().filterResponse(new HttpClosingFilterInputStream(httpClient, proxyResponse));

        // send response
        return sendResponse(responseEntity, proxyResponse);
    }

    public Response passthru(InputStream stream) {
        final SendableResource response = new SendableResource(new StreamStreamingOutput(stream))
                .setStatus(OK);
        return send(response);
    }

    public Response passthru(ContainerRequest request) { return passthru(request.getEntityStream()); }

    public Response applyRulesAndSendResponse(ContainerRequest request,
                                              Account account,
                                              Device device,
                                              String[] matcherIds) throws IOException {

        if (empty(matcherIds)) return passthru(null, request);

        // init rules
        final List<AppRuleHarness> ruleHarnesses = initRules(account, device, matcherIds);
        final AppRuleHarness firstRule = ruleHarnesses.get(0);

        final InputStream responseEntity = firstRule.getDriver().filterResponse(request.getEntityStream());
        return sendResponse(responseEntity);
    }

    public List<AppRuleHarness> initRules(Account account, Device device, String[] matcherIds) {
        final List<AppMatcher> matchers = matcherDAO.findByUuids(matcherIds);
        if (matchers.size() != matcherIds.length) {
            log.warn("initRules: duplicate rules, or could not resolve some rule(s)");
        }
        List<AppRuleHarness> ruleHarnesses = new ArrayList<>();
        for (AppMatcher m : matchers) {
            final AppRule rule = ruleDAO.findByUuid(m.getRule());
            if (rule == null) {
                log.warn("initRules: rule not found ("+m.getRule()+") for matcher: "+m.getUuid());
                continue;
            }
            ruleHarnesses.add(new AppRuleHarness(m, rule));
        }
        ruleHarnesses = initRules(account, device, ruleHarnesses);
        return ruleHarnesses;
    }

    public List<AppRuleHarness> initRules(Account account, Device device, List<AppRuleHarness> rules) {
        for (AppRuleHarness h : rules) {
            final RuleDriver ruleDriver = driverDAO.findByUuid(h.getRule().getDriver());
            if (ruleDriver == null) {
                log.warn("get: driver not found: "+h.getRule().getDriver());
                continue;
            }
            final AppRuleDriver driver = autowire(configuration.getApplicationContext(), h.getRule().initDriver(ruleDriver, h.getMatcher()));
            driver.setSessionId(account.getApiToken());
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

}
