package bubble.resources.app;

import bubble.model.account.Account;
import bubble.model.app.AppDataDriver;
import bubble.model.app.config.AppDataView;
import bubble.model.app.BubbleApp;
import bubble.model.device.Device;
import bubble.service.cloud.DeviceIdService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.EP_VIEW;
import static bubble.ApiConstants.getRemoteHost;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public class AppsResource extends AppsResourceBase {

    public AppsResource(Account account) { super(account); }

    @Autowired private DeviceIdService deviceIdService;

    @GET @Path("/{id}"+EP_VIEW+"/{view}")
    public Response search(@Context Request req,
                           @Context ContainerRequest ctx,
                           @PathParam("id") String id,
                           @PathParam("view") String viewName,
                           @QueryParam("n") Integer pageNumber,
                           @QueryParam("sz") Integer pageSize) {
        final SearchQuery query = new SearchQuery(pageNumber == null ? 1 : pageNumber, pageSize == null ? 10 : pageSize);
        return search(req, ctx, id, viewName, query);
    }

    @POST @Path("/{id}"+EP_VIEW+"/{view}")
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
        final Device device = deviceIdService.findDeviceByIp(remoteHost);

        final AppDataDriver driver = app.getDataConfig().getDriver(configuration);
        return ok(driver.query(caller, device, app, null, view, query));
    }

}
