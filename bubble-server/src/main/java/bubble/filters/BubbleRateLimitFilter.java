package bubble.filters;

import bubble.model.account.Account;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.filters.RateLimitFilter;
import org.springframework.stereotype.Service;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.ext.Provider;
import java.security.Principal;
import java.util.List;

import static bubble.ApiConstants.FILTER_HTTP_ENDPOINT;
import static bubble.ApiConstants.SESSION_HEADER;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

@Provider @Service @NoArgsConstructor @Slf4j
public class BubbleRateLimitFilter extends RateLimitFilter {

    @Override protected String getToken(ContainerRequestContext request) { return request.getHeaderString(SESSION_HEADER); }

    @Override protected List<String> getKeys(ContainerRequestContext request) {
        return super.getKeys(request);
    }

    // super-admins have unlimited API usage. helpful when populating models
    @Override protected boolean allowUnlimitedUse(Principal user, ContainerRequestContext request) {
        try {
            return ((Account) user).admin() || request.getUriInfo().getPath().startsWith(FILTER_HTTP_ENDPOINT);
        } catch (Exception e) {
            log.warn("allowUnlimitedUse: "+shortError(e));
            return false;
        }
    }

}
