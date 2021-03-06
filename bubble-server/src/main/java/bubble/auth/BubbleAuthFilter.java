/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.auth;

import bubble.dao.account.AccountDAO;
import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.SingletonSet;
import org.cobbzilla.wizard.filters.auth.AuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static bubble.ApiConstants.*;
import static bubble.server.BubbleServer.isRestoreMode;

@Provider @Service @PreMatching
public class BubbleAuthFilter extends AuthFilter<Account> {

    public static final Set<String> SKIP_AUTH_PREFIXES = new HashSet<>(Arrays.asList(
            AUTH_ENDPOINT, ENTITY_CONFIGS_ENDPOINT,
            PLANS_ENDPOINT, PAYMENT_METHODS_ENDPOINT,
            BUBBLE_MAGIC_ENDPOINT,
            MESSAGES_ENDPOINT, TIMEZONES_ENDPOINT,
            NOTIFY_ENDPOINT, FILTER_HTTP_ENDPOINT, DETECT_ENDPOINT,
            OPENAPI_JSON_ENDPOINT
    ));
    public static final Set<String> SKIP_AUTH_PATHS = new SingletonSet<>(AUTH_ENDPOINT);
    public static final Set<String> SKIP_ALL_AUTH = new SingletonSet<>("/");
    public static final Set<String> SKIP_AUTH_RESTORE = new HashSet<>(Arrays.asList(new String[] {
            AUTH_ENDPOINT + EP_RESTORE + "/", AUTH_ENDPOINT + EP_CONFIGS, AUTH_ENDPOINT + EP_READY,
            BUBBLE_MAGIC_ENDPOINT, NOTIFY_ENDPOINT, MESSAGES_ENDPOINT
    }));
    public static final Set<String> SKIP_AUTH_TEST = new HashSet<>(Arrays.asList(ArrayUtil.append(SKIP_AUTH_PREFIXES.toArray(String[]::new),
            DEBUG_ENDPOINT
    )));

    @Autowired @Getter private AccountDAO accountDAO;
    @Autowired @Getter private BubbleAuthProvider authProvider;
    @Autowired @Getter private BubbleConfiguration configuration;

    @Override public String getAuthTokenHeader() { return SESSION_HEADER; }

    @Override protected Set<String> getSkipAuthPaths() {
        if (configuration.testMode()) return SKIP_AUTH_TEST;
        return isRestoreMode() ? SKIP_AUTH_RESTORE : SKIP_AUTH_PATHS;
    }

    @Override protected Set<String> getSkipAuthPrefixes() {
        if (!accountDAO.activated()) return SKIP_ALL_AUTH;
        if (configuration.testMode()) return SKIP_AUTH_TEST;
        if (isRestoreMode()) return SKIP_AUTH_RESTORE;
        return SKIP_AUTH_PREFIXES;
    }

    @Override protected boolean isPermitted(Account principal, ContainerRequestContext request) {
        return principal != null;
    }

}
