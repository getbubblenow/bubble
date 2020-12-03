/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources;

import bubble.service.cloud.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
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
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;
import static org.cobbzilla.wizard.resources.ResourceUtil.optionalUserPrincipal;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(DETECT_ENDPOINT)
@Service @Slf4j
public class DetectResource {

    @Autowired private GeoService geoService;

    @GET @Path(EP_LOCALE)
    @Operation(tags=API_TAG_BUBBLE_INFO,
            summary="Detect the caller's locale",
            description="Detect the caller's locale",
            responses=@ApiResponse(responseCode=SC_OK, description="an array of locale strings in priority order")
    )
    public Response detectLocales(@Context Request req,
                                  @Context ContainerRequest ctx) {
        return ok(geoService.getSupportedLocales(optionalUserPrincipal(ctx), getRemoteHost(req), normalizeLangHeader(req)));
    }

    @GET @Path(EP_TIMEZONE)
    @Operation(tags=API_TAG_BUBBLE_INFO,
            summary="Detect the caller's time zone",
            description="Detect the caller's time zone",
            responses=@ApiResponse(responseCode=SC_OK, description="the TimeZone ")
    )
    public Response detectTimezone(@Context Request req,
                                   @Context ContainerRequest ctx) {
        return ok(geoService.getTimeZone(optionalUserPrincipal(ctx), getRemoteHost(req)));
    }

}
