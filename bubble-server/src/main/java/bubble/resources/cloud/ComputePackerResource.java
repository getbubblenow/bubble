/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.model.account.Account;
import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import bubble.service.packer.PackerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.API_TAG_ACTIVATION;
import static bubble.resources.cloud.PackerResource.packerNotAllowedForUser;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.forbidden;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class ComputePackerResource {

    @Autowired private BubbleConfiguration configuration;
    @Autowired private PackerService packer;

    private final Account account;
    private final CloudService cloud;

    public ComputePackerResource (Account account, CloudService cloud) {
        this.account = account;
        this.cloud = cloud;
    }

    @GET
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_ACTIVATION,
            summary="List packer images",
            description="List packer images present on this compute cloud",
            responses=@ApiResponse(responseCode=SC_OK, description="a JSON array of PackerImage objects")
    )
    public Response listImages(@Context Request req,
                               @Context ContainerRequest ctx) {
        if (packerNotAllowedForUser(ctx)) return forbidden();
        final ComputeServiceDriver driver = cloud.getComputeDriver(configuration);
        return ok(driver.getAllPackerImages());
    }

    @PUT @Path("/{type}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_ACTIVATION,
            summary="Create a packer image",
            description="Create a packer image on this compute cloud. The packer image will be created if it does not already exist. This call will return immediately and the image will be created in the background.",
            parameters=@Parameter(name="type", description="The type of image to create, either `sage` or `node`", required=true),
            responses=@ApiResponse(responseCode=SC_OK, description="an empty response means the request was accepted")
    )
    public Response writeImages(@Context Request req,
                                @Context ContainerRequest ctx,
                                @PathParam("type") AnsibleInstallType installType) {
        if (packerNotAllowedForUser(ctx)) return forbidden();
        packer.writePackerImages(cloud, installType, null);
        return ok();
    }

}