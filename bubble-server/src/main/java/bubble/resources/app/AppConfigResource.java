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
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import java.util.Map;

import static bubble.ApiConstants.EP_ACTIONS;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.URIUtil.queryParams;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public class AppConfigResource {

    private Account account;
    private BubbleApp app;

    public AppConfigResource (Account account, BubbleApp app) {
        this.account = account;
        this.app = app;
    }

    @Autowired private BubbleConfiguration configuration;

    private AppConfigDriver getConfigDriver() { return app.getDataConfig().getConfigDriver(configuration); }

    @GET
    public Response getConfigView(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(account.getUuid())) return forbidden();
        return ok(app.getDataConfig().getConfigViews());
    }

    @GET @Path("/{view}")
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
