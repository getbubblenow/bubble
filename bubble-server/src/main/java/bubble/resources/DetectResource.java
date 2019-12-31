package bubble.resources;

import bubble.service.cloud.GeoService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.*;
import static bubble.ApiConstants.getRemoteHost;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalid;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(DETECT_ENDPOINT)
@Service @Slf4j
public class DetectResource {

    @Autowired private GeoService geoService;

    @GET @Path(EP_LOCALE)
    public Response detectLocales(@Context Request req,
                                  @Context ContainerRequest ctx) {
        return ok(geoService.getSupportedLocales(optionalUserPrincipal(ctx), getRemoteHost(req), normalizeLangHeader(req)));
    }

    @GET @Path(EP_TIMEZONE)
    public Response detectTimezone(@Context Request req,
                                   @Context ContainerRequest ctx) {
        final String remoteHost = getRemoteHost(req);
        try {
            return ok(geoService.getTimeZone(optionalUserPrincipal(ctx), remoteHost));

        } catch (SimpleViolationException e) {
            return invalid(e);

        } catch (Exception e) {
            return invalid("err.timezone.unknown", e.getMessage());
        }
    }

}
