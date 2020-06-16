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
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class ComputePackerResource {

    @Autowired private BubbleConfiguration configuration;
    @Autowired private PackerService packer;

    private Account account;
    private CloudService cloud;

    public ComputePackerResource (Account account, CloudService cloud) {
        this.account = account;
        this.cloud = cloud;
    }

    @GET
    public Response listImages(@Context Request req,
                               @Context ContainerRequest ctx) {
        final ComputeServiceDriver driver = cloud.getComputeDriver(configuration);
        return ok(driver.getAllPackerImages());
    }

    @PUT @Path("/{type}")
    public Response writeImages(@Context Request req,
                                @Context ContainerRequest ctx,
                                @PathParam("type") AnsibleInstallType installType) {
        packer.writePackerImages(cloud, installType, null);
        return ok();
    }

}