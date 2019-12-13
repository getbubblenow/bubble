package bubble.resources.stream;

import bubble.dao.app.AppMatcherDAO;
import bubble.dao.app.AppRuleDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.URIBean;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static bubble.ApiConstants.PROXY_ENDPOINT;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.CONTENT_TYPE_ANY;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Path(PROXY_ENDPOINT)
@Service @Slf4j
public class ReverseProxyResource {

    @Autowired private BubbleConfiguration configuration;
    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private RuleEngine ruleEngine;

    @Getter(lazy=true) private final int prefixLength = configuration.getHttp().getBaseUri().length() + PROXY_ENDPOINT.length() + 1;

    @GET @Path("/{path: .*}")
    @Consumes(CONTENT_TYPE_ANY)
    @Produces(CONTENT_TYPE_ANY)
    public Response get(@Context ContainerRequest request,
                        @Context ContainerResponse response,
                        @PathParam("path") String path) throws URISyntaxException, IOException {
        final Account account = userPrincipal(request);

        final URIBean ub = getUriBean(request);
        final List<AppMatcher> matchers = matcherDAO.findByAccountAndFqdnAndEnabled(account.getUuid(), ub.getHost());
        if (empty(matchers)) {
            // no matchers, pass-thru
            return ruleEngine.passthru(ub, request);
        } else {
            // find rules by regex
            final Set<AppRuleHarness> rules = new TreeSet<>();
            for (AppMatcher m : matchers) {
                // check for regex match
                if (m.matches(ub.getFullPath())) {
                    // is this a total block?
                    if (m.blocked()) {
                        log.debug("get: matcher("+m.getUuid()+") blocks request, returning 404 Not Found for "+ub.getFullPath());
                        return notFound(ub.getFullPath());
                    }

                    // lookup rule and add to rules array
                    final AppRule rule = ruleDAO.findByUuid(m.getRule());
                    if (rule == null) {
                        log.warn("get: matcher(" + m.getUuid() + "): rule not found: " + m.getRule());
                    } else if (!rule.getApp().equals(m.getApp())) {
                        // sanity check
                        log.error("get: matcher(" + m.getUuid() + "): rule belongs to another site: " + m.getRule());
                    } else {
                        rules.add(new AppRuleHarness(m, rule));
                    }
                }
            }

            // if 'rules' is null or empty, this will passthru
            return ruleEngine.applyRulesAndSendResponse(request, account, ub, new ArrayList<>(rules));
        }
    }

    private URIBean getUriBean(ContainerRequest request) {

        final URIBean ub = new URIBean();
        final String uriString = request.getRequestUri().getPath().substring(getPrefixLength());

        final StringTokenizer st = new StringTokenizer(uriString, "/");
        String host = st.nextToken();
        switch (host.toLowerCase()) {
            case "http": case "https":
                ub.setScheme(host);
                ub.setPort(host.equalsIgnoreCase("http") ? 80 : 443);
                assertHasMoreTokens(st);
                host = st.nextToken();
        }
        final int colonPos = host.indexOf(':');
        if (colonPos != -1) {
            try {
                final String portString = host.substring(colonPos + 1);
                ub.setPort(Integer.parseInt(portString));
            } catch (Exception e) {
                return die("getUriBean: invalid host:port string: \""+host+"\": "+e, e);
            }
            host = host.substring(0, colonPos);
        }
        ub.setHost(host);
        if (st.hasMoreTokens()) {
            ub.setPath(st.nextToken("?"));
            if (st.hasMoreTokens()) {
                ub.setQuery(st.nextToken());
            } else {
                ub.setQuery(request.getRequestUri().getQuery());
            }
        }
        return ub;
    }

    private void assertHasMoreTokens(StringTokenizer st) {
        if (!st.hasMoreTokens()) throw invalidEx("err.rproxy.urlformat");
    }

}
