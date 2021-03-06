/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.app;

import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import bubble.model.app.config.AppDataDriver;
import bubble.model.app.config.AppDataView;
import bubble.model.device.Device;
import bubble.service.device.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.wizard.model.search.SearchQuery.DEFAULT_PAGE_SIZE;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Slf4j
public class AppsResource extends AppsResourceBase {

    public AppsResource(Account account) { super(account); }

    @Autowired private DeviceService deviceService;

    @GET @Path("/{id}"+EP_VIEW+"/{view}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_APPS,
            summary="Search data view for app data",
            description="Search data view for app data",
            parameters={
                    @Parameter(name="id", description="UUID or name of the app", required=true),
                    @Parameter(name="view", description="name of AppDataView to use", required=true),
                    @Parameter(name="n", description="page number of results to return, default is first page"),
                    @Parameter(name="sz", description="number of results per page, default is "+DEFAULT_PAGE_SIZE+", max is "+MAX_SEARCH_PAGE)
            },
            responses={
                    @ApiResponse(responseCode=SC_OK, description="SearchResults object with results")
            }
    )
    public Response search(@Context Request req,
                           @Context ContainerRequest ctx,
                           @PathParam("id") String id,
                           @PathParam("view") String viewName,
                           @QueryParam("n") Integer pageNumber,
                           @QueryParam("sz") Integer pageSize) {
        final SearchQuery query = new SearchQuery(pageNumber == null ? 1 : pageNumber, pageSize == null ? DEFAULT_PAGE_SIZE : pageSize);
        return search(req, ctx, id, viewName, query);
    }

    @POST @Path("/{id}"+EP_VIEW+"/{view}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_APPS,
            summary="Search data view for app data",
            description="Search data view for app data. This uses the AppDataDriver.",
            parameters={
                    @Parameter(name="id", description="UUID or name of the app", required=true),
                    @Parameter(name="view", description="name of AppDataView to use", required=true)
            },
            responses={
                    @ApiResponse(responseCode=SC_OK, description="SearchResults object with results")
            }
    )
    public Response search(@Context Request req,
                           @Context ContainerRequest ctx,
                           @PathParam("id") String id,
                           @PathParam("view") String viewName,
                           SearchQuery query) {

        final Account caller = userPrincipal(ctx);
        final BubbleApp app = getDao().findByAccountAndId(getAccountUuid(ctx), id);
        if (app == null) return notFound(id);
        if (!app.hasDataConfig()) return notFound(id);

        final AppDataView view = app.getDataConfig().getView(viewName);
        if (view == null) return notFound(viewName);

        final String remoteHost = getRemoteHost(req);
        final Device device = deviceService.findDeviceByIp(remoteHost);

        final AppDataDriver driver = app.getDataConfig().getDataDriver(configuration);
        return ok(driver.query(caller, device, app, null, view, query));
    }

}
