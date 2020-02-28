/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.filters;

import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.filters.RateLimitFilter;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired private BubbleConfiguration configuration;

    @Getter private static final String FILTER_PREFIX = FILTER_HTTP_ENDPOINT.substring(1);

    // super-admins have unlimited API usage. helpful when populating models
    // also, the filter endpoint has unlimited API usage, since its tied to end-user usage of the VPN
    @Override protected boolean allowUnlimitedUse(Principal user, ContainerRequestContext request) {
        try {
            final boolean allowUnlimited = (user != null && ((Account) user).admin()) || getPath(request).startsWith(FILTER_PREFIX);
            if (log.isTraceEnabled()) log.trace("allowUnlimitedUse: allowUnlimited="+allowUnlimited+", admin="+(user == null ? "null" : ""+((Account) user).admin())+", path="+ getPath(request) +", filterPrefix="+FILTER_PREFIX);
            return allowUnlimited;
        } catch (Exception e) {
            log.warn("allowUnlimitedUse: "+shortError(e));
            return false;
        }
    }

}
