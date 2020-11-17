/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.account;

import bubble.dao.SessionDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.AccountPolicy;
import bubble.model.account.AuthenticatorRequest;
import bubble.model.account.message.ActionTarget;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static bubble.ApiConstants.G_AUTH;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.userPrincipal;

@Service @Slf4j
public class StandardAuthenticatorService implements AuthenticatorService {

    @Autowired private SessionDAO sessionDAO;
    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService authenticatorTimes = redis.prefixNamespace(getClass().getSimpleName()+"_authentications");

    public String authenticate (Account account, AccountPolicy policy, AuthenticatorRequest request) {
        final AccountContact authenticator = policy.getAuthenticator();
        if (authenticator == null) throw invalidEx("err.authenticator.notConfigured");

        final Integer code = request.intToken();
        if (code == null) throw invalidEx("err.totpToken.invalid");

        final String secret = authenticator.totpInfo().getKey();
        if (G_AUTH.authorize(secret, code)) {
            final String sessionToken = request.startSession() ? sessionDAO.create(account) : account.getToken();
            if (sessionToken == null && request.startSession()) throw invalidEx("err.totpToken.noSession");
            if (sessionToken != null) {
                markAsAuthenticated(sessionToken, policy);
                return sessionToken;
            }
            return null;

        } else {
            throw invalidEx("err.totpToken.invalid");
        }
    }

    public void markAsAuthenticated(String sessionToken, AccountPolicy policy) {
        getAuthenticatorTimes().set(sessionToken, String.valueOf(now()), EX, policy.getAuthenticatorTimeout()/1000);
    }

    public boolean isAuthenticated (String sessionToken) { return getAuthenticatorTimes().get(sessionToken) != null; }

    public void ensureAuthenticated(ContainerRequest ctx) { ensureAuthenticated(ctx, null); }

    public void ensureAuthenticated(ContainerRequest ctx, ActionTarget target) {
        final Account account = userPrincipal(ctx);
        final AccountPolicy policy = policyDAO.findSingleByAccount(account.getUuid());
        ensureAuthenticated(account, policy, target);
    }

    public void ensureAuthenticated(ContainerRequest ctx, AccountPolicy policy, ActionTarget target) {
        final Account account = userPrincipal(ctx);
        ensureAuthenticated(account, policy, target);
    }

    public void ensureAuthenticated(Account account, AccountPolicy policy, ActionTarget target) {
        if (account == null || policy == null || !policy.hasVerifiedAuthenticator()) return;
        if (target != null) {
            final AccountContact authenticator = policy.getAuthenticator();
            if (authenticator == null) {
                log.info("ensureAuthenticated("+account.getName()+"): no authenticator configured");
                return;
            }
            switch (target) {
                case account: if (!authenticator.requiredForAccountOperations()) return; break;
                case network: if (!authenticator.requiredForNetworkOperations()) return; break;
                default: throw invalidEx("err.actionTarget.invalid");
            }
        }
        if (!isAuthenticated(account.getToken())) throw invalidEx("err.totpToken.invalid");
    }

    public boolean flush(String sessionToken) {
        final String exists = getAuthenticatorTimes().get(sessionToken);
        getAuthenticatorTimes().del(sessionToken);
        return exists != null;
    }

    public void updateExpiration(ContainerRequest ctx, AccountPolicy policy) {
        final Account account = userPrincipal(ctx);
        final String sessionToken = account.getToken();
        if (flush(sessionToken)) {
            markAsAuthenticated(sessionToken, policy);
        }
    }
}
