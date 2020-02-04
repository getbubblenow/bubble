package bubble.filters;

import bubble.model.account.Account;
import lombok.NoArgsConstructor;
import org.cobbzilla.wizard.filters.RateLimitFilter;
import org.springframework.stereotype.Service;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.ext.Provider;
import java.security.Principal;
import java.util.List;

import static bubble.ApiConstants.SESSION_HEADER;

@Provider @Service @NoArgsConstructor
public class BubbleRateLimitFilter extends RateLimitFilter {

    @Override protected String getToken(ContainerRequestContext request) { return request.getHeaderString(SESSION_HEADER); }

    @Override protected List<String> getKeys(ContainerRequestContext request) {
        return super.getKeys(request);
    }

    // super-admins have unlimited API usage. helpful when populating models
    @Override protected boolean allowUnlimitedUse(Principal user) {
        try {
            return ((Account) user).admin();
        } catch (Exception e) {
            return false;
        }
    }

}
