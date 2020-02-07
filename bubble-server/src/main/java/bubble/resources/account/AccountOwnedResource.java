package bubble.resources.account;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.account.HasAccountNoName;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class AccountOwnedResource<E extends HasAccount, DAO extends AccountOwnedEntityDAO<E>> {

    @Autowired protected AccountDAO accountDAO;
    @Autowired protected BubbleConfiguration configuration;

    protected final Account account;

    public AccountOwnedResource (Account account) { this.account = account; }

    @Getter(lazy=true) private final Class<E> entityClass = getFirstTypeParam(getClass(), HasAccount.class);
    @Getter(lazy=true) private final DAO dao = (DAO) configuration.getDaoForEntityClass(getEntityClass());

    // some subclasses override these to enforce policy
    protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, E request) { return true; }
    protected boolean canUpdate(ContainerRequest ctx, Account caller, E found, E request) { return true; }
    protected boolean canDelete(ContainerRequest ctx, Account caller, E found) { return true; }

    protected boolean isReadOnly(ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (caller.admin()) return false;
        if (account == null) return true;
        if (!account.getUuid().equals(caller.getUuid())) throw forbiddenEx();
        return false;
    }

    @GET
    public Response listEntities(@Context Request req,
                                 @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()
                && !caller.getUuid().equals(getAccountUuid(ctx))
                && !caller.getParent().equals(getAccountUuid(ctx))) {
            return notFound();
        }
        return ok(populate(ctx, list(req, ctx)));
    }

    protected List<E> list(Request req, ContainerRequest ctx) { return list(ctx); }

    protected String getAccountUuid(ContainerRequest ctx) { return getAccountUuid(account, ctx); }

    public Account getAccount(Account account, ContainerRequest ctx) { return accountDAO.findByUuid(getAccountUuid(account, ctx)); }

    public static String getAccountUuid(Account account, ContainerRequest ctx) {
        if (account != null) return account.getUuid();
        final Account caller = userPrincipal(ctx);
        if (caller.admin()) return caller.getUuid();
        return caller.getParent();
    }

    public static String validateAccountUuid(Account account, ContainerRequest ctx, Account caller) {
        final String accountUuid = getAccountUuid(account, ctx);
        if (!caller.admin() && !caller.getUuid().equals(accountUuid)) throw notFoundEx();
        return accountUuid;
    }

    protected List<E> list(ContainerRequest ctx) {
        return getDao().findByAccount(getAccountUuid(ctx));
    }

    protected E find(ContainerRequest ctx, String id) {
        return getDao().findByAccountAndId(getAccountUuid(ctx), id);
    }

    protected E findAlternate(ContainerRequest ctx, E request) { return null; }
    protected E findAlternate(ContainerRequest ctx, String id) { return null; }

    protected E findAlternateForCreate(ContainerRequest ctx, E request) { return findAlternate(ctx, request); }
    protected E findAlternateForUpdate(ContainerRequest ctx, String id) { return findAlternate(ctx, id); }
    protected E findAlternateForDelete(ContainerRequest ctx, String id) { return findAlternate(ctx, id); }

    protected List<E> populate(ContainerRequest ctx, List<E> entities) {
        for (E e : entities) populate(ctx, e);
        return entities;
    }

    protected E populate(ContainerRequest ctx, E entity) { return entity; }

    @GET @Path("/{id}")
    public Response view(@Context ContainerRequest ctx,
                         @PathParam("id") String id) {

        final Account caller = userPrincipal(ctx);
        final String accountUuid = getAccountUuid(ctx);
        if (!caller.admin() && !caller.getUuid().equals(accountUuid)) return notFound();
        E found = find(ctx, id);

        if (found == null) {
            found = findAlternate(ctx, id);
            if (found == null) return notFound(id);
        }
        if (!found.getAccount().equals(caller.getUuid()) && !caller.admin()) return notFound(id);

        return ok(populate(ctx, found));
    }

    @PUT
    public Response create(@Context Request req,
                           @Context ContainerRequest ctx,
                           E request) {
        if (request == null) return invalid("err.request.invalid");
        final Account caller = checkEditable(ctx);
        E found = find(ctx, request.getName());
        if (found == null) {
            found = findAlternateForCreate(ctx, request);
        }
        if (found != null) {
            if (!canUpdate(ctx, caller, found, request)) return ok(found);
            setReferences(ctx, caller, request);
            found.update(request);
            return ok(getDao().update(found));
        }

        if (!canCreate(req, ctx, caller, request)) return invalid("err.cannotCreate", "Create entity not allowed", request.getName());

        final E toCreate = setReferences(ctx, caller, instantiate(getEntityClass(), request).setAccount(getAccountUuid(ctx)));
        return ok(daoCreate(toCreate));
    }

    protected Object daoCreate(E toCreate) { return getDao().create(toCreate); }

    protected E setReferences(ContainerRequest ctx, Account caller, E e) { return e; }

    @POST @Path("/{id}")
    public Response update(@Context ContainerRequest ctx,
                           @PathParam("id") String id,
                           E request) {
        if (request == null) return invalid("err.request.invalid");
        final Account caller = checkEditable(ctx);
        E found = find(ctx, id);
        if (found == null) {
            found = findAlternateForUpdate(ctx, id);
            if (found == null) return notFound(id);
        }
        if (!(found instanceof HasAccountNoName) && !canChangeName() && request.hasName() && !request.getName().equals(found.getName())) {
            return notFound(id+"/"+request.getName());
        }

        if (!canUpdate(ctx, caller, found, request)) return invalid("err.cannotUpdate", "Update entity not allowed", request.getName());
        found.update(request);
        return ok(getDao().update(found.setAccount(getAccountUuid(ctx))));
    }

    protected boolean canChangeName() { return false; }

    @DELETE @Path("/{id}")
    public Response delete(@Context ContainerRequest ctx,
                           @PathParam("id") String id) {
        final Account caller = checkEditable(ctx);
        E found = find(ctx, id);
        if (found == null) {
            found = findAlternateForDelete(ctx, id);
            if (found == null) return notFound(id);
        }

        if (!canDelete(ctx, caller, found)) return forbidden();

        getDao().delete(found.getUuid());
        return ok(found);
    }

    protected Account checkEditable(ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (caller.admin()) return caller;

        if (isReadOnly(ctx)) throw forbiddenEx();

        if (!caller.getUuid().equals(getAccountUuid(ctx))) throw notFoundEx();

        return caller;
    }

}
