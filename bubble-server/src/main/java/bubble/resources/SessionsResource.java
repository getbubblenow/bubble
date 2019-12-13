package bubble.resources;

import bubble.dao.SessionDAO;
import bubble.model.account.Account;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.cobbzilla.wizard.resources.AbstractSessionsResource;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.SESSIONS_ENDPOINT;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(SESSIONS_ENDPOINT)
@Service @Slf4j
public class SessionsResource extends AbstractSessionsResource<Account> {

    @Autowired @Getter private SessionDAO sessionDAO;

    @GET
    public Response me(@Context ContainerRequest ctx) {
        final Account found = optionalUserPrincipal(ctx);
        return ok(found);
    }

}