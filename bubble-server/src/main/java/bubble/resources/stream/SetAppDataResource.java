package bubble.resources.stream;

import bubble.dao.app.AppDataDAO;
import bubble.model.app.AppData;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.string.Base64;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static bubble.ApiConstants.DATA_ENDPOINT;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;

@Path(DATA_ENDPOINT)
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Service @Slf4j
public class SetAppDataResource {

    @Autowired private AppDataDAO dataDAO;

    @GET @Path("/{appDataBase64: .+}")
    public Response get(@Context ContainerRequest request,
                        @Context ContainerResponse response,
                        @PathParam("appDataBase64") String appDataBase64) throws IOException {
        final AppData appData = json(new String(Base64.decode(appDataBase64)), AppData.class);
        return ok(dataDAO.set(appData));
    }

}
