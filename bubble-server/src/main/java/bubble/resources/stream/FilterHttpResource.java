package bubble.resources.stream;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.AppMatcherDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
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

import static bubble.ApiConstants.*;
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
    @Autowired private BubbleConfiguration configuration;

    @POST @Path(EP_MATCHERS)
    @Produces(APPLICATION_JSON)
    public Response matchersForFqdn(@Context Request req,
                                    @Context ContainerRequest request,
                                    FilterMatchersRequest filterRequest) {
        final String remoteHost = getRemoteHost(req);
        final String remoteAddr = req.getRemoteAddr();
        log.info("matchersForFqdn: remoteHost="+remoteHost+", remoteAddr="+remoteAddr);
        if (!isLocalIpv4(remoteAddr)) return forbidden();

        // todo: determine device+account based on remoteHost
        final String accountUuid = configuration.getThisNode().getAccount();
        final Account caller = accountDAO.findByUuid(accountUuid);

        final String fqdn = filterRequest.getFqdn();
        final List<AppMatcher> matchers = matcherDAO.findByAccountAndFqdnAndEnabled(accountUuid, fqdn);
        log.info("matchersForFqdn: returning "+matchers.size()+" matchers");
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
        return ok(matchers);
    }

    @POST @Path(EP_APPLY+"/{matchers}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response filterHttp(@Context Request req,
                               @Context ContainerRequest request,
                               @PathParam("matchers") String matchersJson) throws IOException {
        final String remoteHost = getRemoteHost(req);
        final String remoteAddr = req.getRemoteAddr();
        if (!isLocalIpv4(remoteAddr)) return forbidden();

        // todo: determine device+account based on remoteHost
        final String accountUuid = configuration.getThisNode().getAccount();

        final Account caller = accountDAO.findByUuid(accountUuid);
        if (caller == null) {
            log.info("filterHttp: caller not found (accountUuid="+accountUuid+"), returning passthru");
            return passthru(request);
        }

        if (missing(matchersJson)) {
            log.info("filterHttp: no matchers provided, returning passthru");
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

        return ruleEngine.applyRulesAndSendResponse(request, caller, matchers);
    }

    private boolean missing(String v) { return v == null || v.trim().length() == 0; }

    public Response passthru(@Context ContainerRequest request) { return ruleEngine.passthru(request); }

}
