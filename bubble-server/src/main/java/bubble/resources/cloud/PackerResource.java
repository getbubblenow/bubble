/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.model.account.Account;
import bubble.service.packer.PackerService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.MapBuilder;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
@Slf4j
public class PackerResource {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";

    private final Account account;

    public PackerResource(Account account) { this.account = account; }

    @Autowired private PackerService packerService;

    @GET
    public Response listAllStatus(@Context Request req,
                                  @Context ContainerRequest ctx) {
        if (packerNotAllowedForUser(ctx)) return forbidden();
        return ok(MapBuilder.build(new Object[][] {
                {STATUS_RUNNING, packerService.getActiveSummary(account.getUuid()) },
                {STATUS_COMPLETED, packerService.getCompletedSummary(account.getUuid()) }
        }));
    }

    @GET @Path(STATUS_RUNNING)
    public Response listRunningBuilds(@Context Request req,
                                      @Context ContainerRequest ctx) {
        if (packerNotAllowedForUser(ctx)) return forbidden();
        return ok(packerService.getActiveSummary(account.getUuid()));
    }

    @GET @Path(STATUS_COMPLETED)
    public Response listCompletedBuilds(@Context Request req,
                                        @Context ContainerRequest ctx) {
        if (packerNotAllowedForUser(ctx)) return forbidden();
        return ok(packerService.getCompletedSummary(account.getUuid()));
    }

    public static boolean packerNotAllowedForUser(@Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return true;
        return false;
    }

}
