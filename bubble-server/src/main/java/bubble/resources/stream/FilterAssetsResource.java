package bubble.resources.stream;

import bubble.dao.app.AppMessageDAO;
import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.cobbzilla.wizard.resources.ResourceUtil.ok;

public class FilterAssetsResource {

    private Account account;
    private BubbleApp app;

    public FilterAssetsResource (Account account, BubbleApp app) {
        this.account = account;
        this.app = app;
    }

    @Autowired private AppMessageDAO appMessageDAO;

    @GET @Path("/{assetId}")
    @Produces(MediaType.WILDCARD)
    public Response filterHttp(@Context Request req,
                               @Context ContainerRequest request,
                               @PathParam("assetId") String assetId) {
        // todo: locate and send asset
        return ok();
    }

}
