/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.account;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.account.HasAccountNoName;
import bubble.server.BubbleConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;

import static bubble.ApiConstants.API_TAG_ACCOUNT_OBJECTS;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

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
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_ACCOUNT_OBJECTS},
            summary="List objects",
            description="List objects",
            responses={@ApiResponse(responseCode=SC_OK, description="an array of objects")}
    )
    public Response listEntities(@Context Request req,
                                 @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()
                && !caller.getUuid().equals(getAccountUuid(ctx))
                && !caller.getParent().equals(getAccountUuid(ctx))) {
            return notFound();
        }
        return ok(sort(populate(ctx, list(req, ctx)), req, ctx));
    }

    protected List<E> list(Request req, ContainerRequest ctx) { return list(ctx); }

    protected List<E> sort(List<E> list, Request req, ContainerRequest ctx) { return list; }

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

    protected E find(Request req, ContainerRequest ctx, String id) { return find(ctx, id); }

    protected E find(ContainerRequest ctx, String id) {
        return getDao().findByAccountAndId(getAccountUuid(ctx), id);
    }

    protected E findAlternate(ContainerRequest ctx, E request) { return null; }
    protected E findAlternate(Request req, ContainerRequest ctx, E request) { return findAlternate(ctx, request); }

    protected E findAlternate(ContainerRequest ctx, String id) { return null; }
    protected E findAlternate(Request req, ContainerRequest ctx, String id) { return findAlternate(ctx, id); }

    protected E findAlternateForCreate(ContainerRequest ctx, E request) { return findAlternate(ctx, request); }
    protected E findAlternateForCreate(Request req, ContainerRequest ctx, E request) { return findAlternateForCreate(ctx, request); }

    protected E findAlternateForUpdate(ContainerRequest ctx, String id) { return findAlternate(ctx, id); }
    protected E findAlternateForUpdate(Request req, ContainerRequest ctx, String id) { return findAlternateForUpdate(ctx, id); }

    protected E findAlternateForDelete(ContainerRequest ctx, String id) { return findAlternate(ctx, id); }
    protected E findAlternateForDelete(Request req, ContainerRequest ctx, String id) { return findAlternateForDelete(ctx, id); }

    protected List<E> populate(ContainerRequest ctx, List<E> entities) {
        for (E e : entities) populate(ctx, e);
        return entities;
    }

    protected E populate(ContainerRequest ctx, E entity) { return entity; }

    @GET @Path("/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_ACCOUNT_OBJECTS},
            summary="Find by identifier",
            description="Find by identifier",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the object, if found"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="if the object does not exist")
            }
    )
    public Response view(@Context Request req,
                         @Context ContainerRequest ctx,
                         @PathParam("id") String id) {

        final Account caller = getAccountForViewById(ctx);
        E found = find(req, ctx, id);

        if (found == null) {
            found = findAlternate(req, ctx, id);
            if (found == null) return notFound(id);
        }
        if (caller != null && !found.getAccount().equals(caller.getUuid()) && !caller.admin()) return notFound(id);

        return ok(populate(ctx, found));
    }

    public Account getAccountForViewById(ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        final String accountUuid = getAccountUuid(ctx);
        if (!caller.admin() && !caller.getUuid().equals(accountUuid)) throw notFoundEx();
        return caller;
    }

    @PUT
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_ACCOUNT_OBJECTS},
            summary="Create a new object",
            description="Create a new object. If validation errors occur, status "+SC_INVALID+" is returned and the response will contain an array of errors. Within each error, the `messageTemplate` field refers to messages that can be localized using the /messages resource",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the object that was created"),
                    @ApiResponse(responseCode=SC_INVALID, description="validation errors occurred",
                    content={@Content(mediaType=APPLICATION_JSON, examples={
                            @ExampleObject(name="validation errors", value="[{\"messageTemplate\": \"some.symbolic.error\", \"message\": \"some default English message\"}]")
                    })})
            }
    )
    public Response create(@Context Request req,
                           @Context ContainerRequest ctx,
                           E request) {
        if (request == null) return invalid("err.request.invalid");
        final Account caller = checkEditable(ctx);
        E found = find(req, ctx, request.getName());
        if (found == null) {
            found = findAlternateForCreate(req, ctx, request);
        }
        if (found != null) {
            if (!canUpdate(ctx, caller, found, request)) return ok(found);
            setReferences(ctx, req, caller, request);
            found.update(request);
            return ok(daoUpdate(found));
        }

        if (!canCreate(req, ctx, caller, request)) return invalid("err.cannotCreate", "Create entity not allowed", request.getName());

        final E toCreate = setReferences(ctx, req, caller, instantiate(getEntityClass(), request).setAccount(getAccountUuid(ctx)));
        return ok(daoCreate(toCreate));
    }

    protected Object daoCreate(E toCreate) { return getDao().create(toCreate); }
    protected Object daoUpdate(E toUpdate) { return getDao().update(toUpdate); }

    protected E setReferences(ContainerRequest ctx, Account caller, E e) { return e; }
    protected E setReferences(ContainerRequest ctx, Request req, Account caller, E e) { return setReferences(ctx, caller, e); }

    @POST @Path("/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_ACCOUNT_OBJECTS},
            summary="Update an existing object",
            description="Update an new object. For many types, the object will be created if it does not exist. If validation errors occur, status "+SC_INVALID+" is returned and the response will contain an array of errors. Within each error, the `messageTemplate` field refers to messages that can be localized using the /messages resource",
            parameters={@Parameter(name="id", description="the UUID (or name, if allowed) of the object to update")},
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the object that was updated"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="no object exists with the given id"),
                    @ApiResponse(responseCode=SC_INVALID, description="validation errors occurred",
                            content={@Content(mediaType=APPLICATION_JSON, examples={
                                    @ExampleObject(name="validation errors", value="[{\"messageTemplate\": \"some.symbolic.error\", \"message\": \"some default English message\"}]")
                            })})
            }
    )
    public Response update(@Context Request req,
                           @Context ContainerRequest ctx,
                           @PathParam("id") String id,
                           E request) {
        if (request == null) return invalid("err.request.invalid");
        final Account caller = checkEditable(ctx);
        E found = find(req, ctx, id);
        if (found == null) {
            found = findAlternateForUpdate(req, ctx, id);
            if (found == null) return notFound(id);
        }
        if (!(found instanceof HasAccountNoName) && !canChangeName() && request.hasName() && !request.getName().equals(found.getName())) {
            return notFound(id+"/"+request.getName());
        }

        if (!canUpdate(ctx, caller, found, request)) return invalid("err.cannotUpdate", "Update entity not allowed", request.getName());
        found.update(request);
        return ok(daoUpdate(found.setAccount(getAccountUuid(ctx))));
    }

    protected boolean canChangeName() { return false; }

    @DELETE @Path("/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_ACCOUNT_OBJECTS},
            summary="Delete an existing object",
            description="Delete an existing object",
            parameters={@Parameter(name="id", description="the UUID (or name, if allowed) of the object to delete")},
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the object that was deleted"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="no object exists with the given id")
            }
    )
    public Response delete(@Context Request req,
                           @Context ContainerRequest ctx,
                           @PathParam("id") String id) {
        final Account caller = checkEditable(ctx);
        E found = find(req, ctx, id);
        if (found == null) {
            found = findAlternateForDelete(req, ctx, id);
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
