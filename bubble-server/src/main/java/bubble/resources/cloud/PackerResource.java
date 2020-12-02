/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.model.account.Account;
import bubble.service.packer.PackerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

import static bubble.ApiConstants.API_TAG_ACTIVATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

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
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_ACTIVATION,
            summary="List status for all packer jobs",
            description="List status for all packer jobs. Must be admin. Returns a JSON object with two properties: `"+STATUS_RUNNING+"` is an array of PackerJobSummary objects. `"+STATUS_COMPLETED+"`, is JSON object whose properties are compute clouds, the value of each being an array of PackerImages.",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="status JSON object"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller is not admin")
            }
    )
    public Response listAllStatus(@Context Request req,
                                  @Context ContainerRequest ctx) {
        if (packerNotAllowedForUser(ctx)) return forbidden();
        return ok(MapBuilder.build(new Object[][] {
                {STATUS_RUNNING, packerService.getActiveSummary(account.getUuid()) },
                {STATUS_COMPLETED, packerService.getCompletedSummary(account.getUuid()) }
        }));
    }

    @GET @Path(STATUS_RUNNING)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_ACTIVATION,
            summary="List status for running packer jobs",
            description="List status for running packer jobs. Must be admin. Returns an array of PackerJobSummary objects",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="PackerJobSummary object"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller is not admin")
            }
    )
    public Response listRunningBuilds(@Context Request req,
                                      @Context ContainerRequest ctx) {
        if (packerNotAllowedForUser(ctx)) return forbidden();
        return ok(packerService.getActiveSummary(account.getUuid()));
    }

    @GET @Path(STATUS_COMPLETED)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_ACTIVATION,
            summary="List completed packer jobs",
            description="List completed packer jobs. Must be admin. Returns a JSON object whose properties are compute clouds, the value of each being an array of PackerImages.",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="Map of Cloud -> PackerImage[]"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller is not admin")
            }
    )
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
