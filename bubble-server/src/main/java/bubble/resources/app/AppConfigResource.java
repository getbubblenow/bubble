/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.app;

import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import bubble.model.app.config.AppConfigDriver;
import bubble.model.app.config.AppConfigView;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.Map;

import static bubble.ApiConstants.API_TAG_APPS;
import static bubble.ApiConstants.EP_ACTIONS;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.util.http.URIUtil.queryParams;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class AppConfigResource {

    private final Account account;
    private final BubbleApp app;

    public AppConfigResource (Account account, BubbleApp app) {
        this.account = account;
        this.app = app;
    }

    @Autowired private BubbleConfiguration configuration;

    private AppConfigDriver getConfigDriver() { return app.getDataConfig().getConfigDriver(configuration); }

    @GET
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_APPS,
            summary="List config views for Bubble app",
            description="List config views for Bubble app. The web UI uses this to render the list of configuration links",
            responses=@ApiResponse(responseCode=SC_OK, description="a JSON array of AppConfigView objects")
    )
    public Response listConfigViews(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(account.getUuid())) return forbidden();
        return ok(app.getDataConfig().getConfigViews());
    }

    @GET @Path("/{view}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_APPS,
            summary="Get a specific config view for Bubble app",
            description="Get a specific config view for Bubble app. Returns a JSON object corresponding to the view",
            parameters=@Parameter(name="view", description="config view name"),
            responses=@ApiResponse(responseCode=SC_OK, description="a JSON object")
    )
    public Response getConfigView(@Context Request req,
                                  @Context ContainerRequest ctx,
                                  @PathParam("view") String view) {

        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(account.getUuid())) return forbidden();

        final AppConfigView configView = app.getDataConfig().getConfigView(view);
        if (configView == null) return notFound(view);

        final Map<String, String> queryParams = queryParams(req.getQueryString());

        final AppConfigDriver configDriver = getConfigDriver();
        return ok(configDriver.getView(account, app, view, queryParams));
    }

    @PUT @Path("/{view}"+EP_ACTIONS+"/{action}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_APPS,
            summary="Take a config action",
            description="Take a config action",
            parameters={
                    @Parameter(name="view", description="config view name"),
                    @Parameter(name="action", description="action name")
            },
            responses=@ApiResponse(responseCode=SC_OK, description="a JSON object")
    )
    public Response takeConfigAppAction(@Context Request req,
                                         @Context ContainerRequest ctx,
                                         @PathParam("view") String view,
                                         @PathParam("action") String action,
                                         JsonNode data) {

        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(account.getUuid())) return forbidden();

        final AppConfigView configView = app.getDataConfig().getConfigView(view);
        if (configView == null) return notFound(view);

        if (empty(action)) return invalid("err.action.required");

        final Map<String, String> queryParams = queryParams(req.getQueryString());

        final AppConfigDriver configDriver = getConfigDriver();
        return ok(configDriver.takeAppAction(account, app, view, action, queryParams, data));
    }

    @POST @Path("/{view}"+EP_ACTIONS+"/{action}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_APPS,
            summary="Take a config item action",
            description="Take a config item action",
            parameters={
                    @Parameter(name="view", description="config view name"),
                    @Parameter(name="action", description="action name"),
                    @Parameter(name="id", description="item id")
            },
            responses=@ApiResponse(responseCode=SC_OK, description="a JSON object")
    )
    public Response takeConfigItemAction(@Context Request req,
                                         @Context ContainerRequest ctx,
                                         @PathParam("view") String view,
                                         @PathParam("action") String action,
                                         @QueryParam("id") String id,
                                         JsonNode data) {

        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(account.getUuid())) return forbidden();

        final AppConfigView configView = app.getDataConfig().getConfigView(view);
        if (configView == null) return notFound(view);

        if (empty(id)) return invalid("err.id.required");
        if (empty(action)) return invalid("err.action.required");

        final Map<String, String> queryParams = queryParams(req.getQueryString());

        final AppConfigDriver configDriver = getConfigDriver();
        return ok(configDriver.takeItemAction(account, app, view, action, id, queryParams, data));
    }

}
