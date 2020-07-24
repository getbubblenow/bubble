package bubble.resources.account;

import bubble.dao.SessionDAO;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.dao.account.TrustedClientDAO;
import bubble.model.account.*;
import bubble.model.account.message.ActionTarget;
import bubble.service.account.StandardAuthenticatorService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;

import static bubble.ApiConstants.EP_DELETE;
import static bubble.resources.account.AuthResource.newLoginSession;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.cache.redis.RedisService.PX;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class TrustedAuthResource {

    private static final long MAX_TRUST_TIME_OFFSET = SECONDS.toMillis(20);

    @Autowired private AccountDAO accountDAO;
    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private SessionDAO sessionDAO;
    @Autowired private StandardAuthenticatorService authenticatorService;
    @Autowired private TrustedClientDAO trustedClientDAO;

    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService trustHashCache = redis.prefixNamespace("loginTrustedClient");

    @PUT
    public Response trustClient(@Context ContainerRequest ctx,
                                AccountLoginRequest request) {
        final Account caller = userPrincipal(ctx);
        final Account account = validateAccountLogin(request.getEmail(), request.getPassword());
        if (!account.getUuid().equals(caller.getUuid())) return notFound(request.getEmail());

        final AccountPolicy policy = policyDAO.findSingleByAccount(account.getUuid());
        authenticatorService.ensureAuthenticated(ctx, policy, ActionTarget.account);

        return ok(new TrustedClientResponse(trustedClientDAO.create(new TrustedClient().setAccount(account.getUuid())).getTrustId()));
    }

    @POST
    public Response loginTrustedClient(@Context ContainerRequest ctx,
                                       @Valid TrustedClientLoginRequest request) {
        final Account account = validateTrustedCall(request);
        if (!request.hasEmail()) return invalid("err.email.required", "email is required");
        if (!request.hasPassword()) return invalid("err.password.required", "password is required");
        final Account validated = validateAccountLogin(request.getEmail(), request.getPassword());
        if (!validated.getUuid().equals(account.getUuid())) return notFound(request.getEmail());

        final TrustedClient trusted = findTrustedClient(account, request);
        log.info("loginTrustedClient: logging in trusted: "+account.getName());
        return ok(account.setToken(newLoginSession(account, accountDAO, sessionDAO)));
    }

    @POST @Path(EP_DELETE)
    public Response removeTrustedClient(@Context ContainerRequest ctx,
                                        @Valid TrustedClientLoginRequest request) {
        final Account caller = userPrincipal(ctx);
        final Account validated = validateAccountLogin(request.getEmail(), request.getPassword());
        if (!validated.getUuid().equals(caller.getUuid())) return notFound(request.getEmail());

        final Account account = validateTrustedCall(request);
        final TrustedClient trusted = findTrustedClient(account, request);
        trustedClientDAO.delete(trusted.getUuid());
        return ok_empty();
    }

    private Account validateAccountLogin(String email, String password) {
        if (empty(email)) throw invalidEx("err.email.required", "email is required");
        if (empty(password)) throw invalidEx("err.password.required", "password is required");
        final Account account = accountDAO.findByEmail(email);
        if (account == null || account.deleted()) throw notFoundEx(email);
        if (!account.getHashedPassword().isCorrectPassword(password)) {
            throw notFoundEx(email);
        }
        if (account.suspended()) throw invalidEx("err.account.suspended");
        return account;
    }

    private Account validateTrustedCall(TrustedClientLoginRequest request) {
        final Account account = accountDAO.findByEmail(request.getEmail());
        if (account == null) throw notFoundEx();
        if (Math.abs(now() - request.getTime()) > MAX_TRUST_TIME_OFFSET) {
            log.warn("validateTrustedCall: time in salt was too old or too new");
            throw invalidEx("err.trustHash.invalid");
        }
        if (getTrustHashCache().get(request.getTrustHash()) != null) {
            log.warn("validateTrustedCall: trustHash has already been used");
            throw invalidEx("err.trustHash.invalid");
        }
        getTrustHashCache().set(request.getTrustHash(), request.getTrustHash(), PX, MAX_TRUST_TIME_OFFSET*2);
        return account;
    }

    private TrustedClient findTrustedClient(Account account, TrustedClientLoginRequest request) {
        final List<TrustedClient> trustedClients = trustedClientDAO.findByAccount(account.getUuid());
        final TrustedClient trusted = trustedClients.stream().filter(c -> c.isValid(request)).findFirst().orElse(null);
        if (trusted == null) {
            log.warn("findTrustedClient: no TrustedClient found for salt/hash");
            throw notFoundEx(request.getTrustHash());
        }
        return trusted;
    }

}
