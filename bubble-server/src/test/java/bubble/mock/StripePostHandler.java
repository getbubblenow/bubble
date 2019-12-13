package bubble.mock;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.stereotype.Service;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_FORM_URL_ENCODED;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok_empty;

@Service @Slf4j
@Path("/stripe")
public class StripePostHandler {

    @Consumes(APPLICATION_FORM_URL_ENCODED)
    @Produces(APPLICATION_JSON)
    @POST
    public Response receiveStripeToken(@Context ContainerRequest ctx) {
//        log.info("bubble_uuid="+bubble_uuid+", stripeToken="+stripeToken);
        log.info("wtf");
        return ok_empty();
    }

}
