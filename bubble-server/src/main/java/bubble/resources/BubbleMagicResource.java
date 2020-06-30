/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.stereotype.Service;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.BUBBLE_MAGIC_ENDPOINT;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(BUBBLE_MAGIC_ENDPOINT)
@Service @Slf4j
public class BubbleMagicResource {

    @GET
    public Response get(@Context ContainerRequest ctx) {
        return ok("you are ok. the magic is ok too.");
    }

}
