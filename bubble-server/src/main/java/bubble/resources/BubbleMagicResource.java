package bubble.resources;

import bubble.resources.driver.DriverAssetsResource;
import bubble.server.BubbleConfiguration;
import bubble.service.cloud.RequestCoordinationService;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.BUBBLE_MAGIC_ENDPOINT;
import static bubble.server.BubbleServer.isRestoreMode;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(BUBBLE_MAGIC_ENDPOINT)
@Service @Slf4j
public class BubbleMagicResource {

    @Autowired private BubbleConfiguration configuration;
    @Autowired private RequestCoordinationService requestService;

    @GET
    public Response get(@Context ContainerRequest ctx) {
        return ok("you are ok. the magic is ok too.");
    }

    @Path("{requestId}/{driverClass}")
    public DriverAssetsResource getAssets(@Context ContainerRequest ctx,
                                          @PathParam("requestId") String requestId,
                                          @PathParam("driverClass") String driverClass) {
        if (isRestoreMode()) throw forbiddenEx();
        final String json = requestService.get(driverClass, requestId);
        if (json == null) throw notFoundEx(requestId);
        return configuration.subResource(DriverAssetsResource.class, requestId, driverClass);
    }

}
