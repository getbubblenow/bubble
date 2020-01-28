package bubble.resources.app;

import bubble.model.account.Account;
import bubble.model.app.AppDataDriver;
import bubble.model.app.AppDataView;
import bubble.model.app.BubbleApp;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.EP_VIEW;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public class AppsResource extends AppsResourceBase {

    public AppsResource(Account account) { super(account); }

    @GET @Path("/{id}"+EP_VIEW+"/{view}")
    public Response search(@Context ContainerRequest ctx,
                           @PathParam("id") String id,
                           @PathParam("view") String viewName,
                           @QueryParam("n") Integer pageNumber,
                           @QueryParam("sz") Integer pageSize) {
        final SearchQuery query = new SearchQuery(pageNumber == null ? 1 : pageNumber, pageSize == null ? 10 : pageSize);
        return search(ctx, id, viewName, query);
    }

    @POST @Path("/{id}"+EP_VIEW+"/{view}")
    public Response search(@Context ContainerRequest ctx,
                           @PathParam("id") String id,
                           @PathParam("view") String viewName,
                           SearchQuery query) {

        final Account caller = userPrincipal(ctx);
        final BubbleApp app = getDao().findByAccountAndId(getAccountUuid(ctx), id);
        if (app == null) return notFound(id);
        if (!app.hasDataConfig()) return notFound(id);

        final AppDataView view = app.getDataConfig().getView(viewName);
        if (view == null) return notFound(viewName);

        final AppDataDriver driver = app.getDataConfig().getDriver(configuration);
        return ok(driver.query(caller, app.getDataConfig(), view, query));
    }

}
