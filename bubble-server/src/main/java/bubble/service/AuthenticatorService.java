package bubble.service;

import bubble.dao.SessionDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.AccountPolicy;
import bubble.model.account.AuthenticatorRequest;
import bubble.model.account.message.ActionTarget;
import lombok.Getter;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static bubble.ApiConstants.G_AUTH;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.userPrincipal;

@Service
public class AuthenticatorService {

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
            if (sessionToken == null) throw invalidEx("err.totpToken.noSession");
            getAuthenticatorTimes().set(sessionToken, String.valueOf(now()), EX, policy.getAuthenticatorTimeout()/1000);
            return sessionToken;

        } else {
            throw invalidEx("err.totpToken.invalid");
        }
    }

    public boolean isAuthenticated (String sessionToken) { return getAuthenticatorTimes().get(sessionToken) != null; }

    public void ensureAuthenticated(ContainerRequest ctx) { ensureAuthenticated(ctx, null); }

    public void ensureAuthenticated(ContainerRequest ctx, ActionTarget target) {
        final Account account = userPrincipal(ctx);
        final AccountPolicy policy = policyDAO.findSingleByAccount(account.getUuid());
        checkAuth(account, policy, target);
    }

    public void ensureAuthenticated(ContainerRequest ctx, AccountPolicy policy, ActionTarget target) {
        final Account account = userPrincipal(ctx);
        checkAuth(account, policy, target);
    }

    private void checkAuth(Account account, AccountPolicy policy, ActionTarget target) {
        if (policy == null || !policy.hasVerifiedAuthenticator()) return;
        if (target != null) {
            final AccountContact authenticator = policy.getAuthenticator();
            switch (target) {
                case account: if (!authenticator.requiredForAccountOperations()) return; break;
                case network: if (!authenticator.requiredForNetworkOperations()) return; break;
                default: throw invalidEx("err.actionTarget.invalid");
            }
        }
        if (!isAuthenticated(account.getToken())) throw invalidEx("err.totpToken.invalid");
    }

    public void flush(String sessionToken) { getAuthenticatorTimes().del(sessionToken); }

}
