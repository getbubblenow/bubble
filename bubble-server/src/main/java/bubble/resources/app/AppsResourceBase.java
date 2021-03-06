/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.app;

import bubble.dao.app.AppMatcherDAO;
import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.BubbleAppDAO;
import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import bubble.model.app.config.AppConfigDriver;
import bubble.model.app.config.AppDataConfig;
import bubble.model.app.config.AppDataDriver;
import bubble.model.app.config.AppDataPresentation;
import bubble.resources.account.AccountOwnedTemplateResource;
import bubble.resources.stream.AppAssetsResource;
import bubble.server.BubbleConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public abstract class AppsResourceBase extends AccountOwnedTemplateResource<BubbleApp, BubbleAppDAO> {

    @Autowired protected BubbleConfiguration configuration;
    @Autowired protected AppRuleDAO ruleDAO;
    @Autowired protected AppMatcherDAO matcherDAO;

    public AppsResourceBase(Account account) { super(account); }

    @Override protected BubbleApp setReferences(ContainerRequest ctx, Account caller, BubbleApp request) {
        if (!request.hasDataConfig()) throw invalidEx("err.dataConfig.required");
        final AppDataConfig dataConfig = request.getDataConfig();
        if (dataConfig.getPresentation() != AppDataPresentation.none) {
            if (!dataConfig.hasDataDriver()) throw invalidEx("err.dataConfig.driver.required");
            try {
                AppDataDriver dataDriver = dataConfig.getDataDriver(configuration);
            } catch (Exception e) {
                throw invalidEx("err.dataConfig.driver.invalid", "Error initializing data driver: "+shortError(e), dataConfig.getDataDriver());
            }
            if (!dataConfig.hasViews()) throw invalidEx("err.dataConfig.views.required");
            if (!dataConfig.hasFields()) throw invalidEx("err.dataConfig.fields.required");
        }
        if (dataConfig.hasConfigDriver()) {
            try {
                final AppConfigDriver configDriver = dataConfig.getConfigDriver(configuration);
            } catch (Exception e) {
                throw invalidEx("err.dataConfig.configDriver.invalid", "Error initializing config driver: "+shortError(e), dataConfig.getDataDriver());
            }
        }
        return super.setReferences(ctx, caller, request);
    }

    @DELETE @Path("/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_APPS,
            summary="Delete Bubble app",
            description="Delete Bubble app",
            parameters=@Parameter(name="id", description="UUID or name of the app", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="HTTP status 200 indicates success",
                            content=@Content(mediaType=APPLICATION_JSON, examples=@ExampleObject(name="returns an empty JSON object", value="{}"))),
                    @ApiResponse(responseCode=SC_INVALID, description="a validation error occurred, for example the token might be invalid")
            }
    )
    @Override public Response delete(@Context Request req,
                                     @Context ContainerRequest ctx,
                                     @PathParam("id") String id) {

        if (isReadOnly(ctx)) return forbidden();

        final BubbleApp found = getDao().findByAccountAndId(getAccountUuid(ctx), id);
        if (found == null) return notFound(id);

        getDao().delete(found.getUuid());
        return ok_empty();
    }

    @POST @Path("/{id}"+EP_ENABLE)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_APPS,
            summary="Enable Bubble app",
            description="Enable Bubble app",
            parameters=@Parameter(name="id", description="UUID or name of the app", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="App successfully enabled")
    )
    public Response enable(@Context ContainerRequest ctx,
                           @PathParam("id") String id) {
        if (isReadOnly(ctx)) return forbidden();
        final BubbleApp found = getDao().findByAccountAndId(getAccountUuid(ctx), id);
        if (found == null) return notFound(id);
        return ok(getDao().update(found.setEnabled(true)));
    }

    @POST @Path("/{id}"+EP_DISABLE)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_APPS,
            summary="Disable Bubble app",
            description="Disable Bubble app",
            parameters=@Parameter(name="id", description="UUID or name of the app", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="App successfully disabled")
    )
    public Response disable(@Context ContainerRequest ctx,
                            @PathParam("id") String id) {
        if (isReadOnly(ctx)) return forbidden();
        final BubbleApp found = getDao().findByAccountAndId(getAccountUuid(ctx), id);
        if (found == null) return notFound(id);
        return ok(getDao().update(found.setEnabled(false)));
    }

    @Path("/{id}"+EP_RULES)
    public AppRulesResource getRules(@Context ContainerRequest ctx,
                                     @PathParam("id") String id) {
        final BubbleApp app = find(ctx, id);
        if (app == null) throw notFoundEx(id);
        return configuration.subResource(AppRulesResource.class, getAccount(account, ctx), app);
    }

    @Path("/{id}"+EP_MATCHERS)
    public AppMatchersResource getMatchers(@Context ContainerRequest ctx,
                                           @PathParam("id") String id) {
        final BubbleApp app = find(ctx, id);
        if (app == null) throw notFoundEx(id);
        return configuration.subResource(AppMatchersResource.class, getAccount(account, ctx), app);
    }

    @Path("/{id}"+EP_DRIVERS)
    public AppDriversResource getDrivers(@Context ContainerRequest ctx,
                                         @PathParam("id") String id) {
        final BubbleApp app = find(ctx, id);
        if (app == null) throw notFoundEx(id);
        return configuration.subResource(AppDriversResource.class, getAccount(account, ctx), app);
    }

    @Path("/{id}"+EP_SITES)
    public AppSitesResource getSites(@Context ContainerRequest ctx,
                                     @PathParam("id") String id) {
        final BubbleApp app = find(ctx, id);
        if (app == null) throw notFoundEx(id);
        return configuration.subResource(AppSitesResource.class, getAccount(account, ctx), app);
    }

    @Path("/{id}"+EP_MESSAGES)
    public AppMessagesResource getMessages(@Context ContainerRequest ctx,
                                           @PathParam("id") String id) {
        final BubbleApp app = find(ctx, id);
        if (app == null) throw notFoundEx(id);
        return configuration.subResource(AppMessagesResource.class, getAccount(account, ctx), app);
    }

    @Path("/{id}"+EP_DATA)
    public AppDataResource getData(@Context ContainerRequest ctx,
                                   @PathParam("id") String id) {
        final BubbleApp app = find(ctx, id);
        if (app == null) throw notFoundEx(id);
        return configuration.subResource(AppDataResource.class, getAccount(account, ctx), app);
    }

    @Path("/{id}"+EP_CONFIG)
    public AppConfigResource getConfigResource(@Context ContainerRequest ctx,
                                             @PathParam("id") String id) {
        final BubbleApp app = find(ctx, id);
        if (app == null || !app.hasDataConfig() || !app.getDataConfig().hasConfigViews()) throw notFoundEx(id);
        return configuration.subResource(AppConfigResource.class, getAccount(account, ctx), app);
    }

    @Path("/{id}"+EP_ASSETS)
    public AppAssetsResource getAppAssetsResource(@Context Request req,
                                                  @Context ContainerRequest ctx,
                                                  @PathParam("id") String id) {
        final BubbleApp app = find(ctx, id);
        if (app == null) throw notFoundEx(id);
        return configuration.subResource(AppAssetsResource.class, getAccount(account, ctx).getLocale(), app);
    }

}
