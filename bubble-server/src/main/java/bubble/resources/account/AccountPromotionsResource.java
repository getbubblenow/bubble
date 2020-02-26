/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources.account;

import bubble.model.account.Account;
import bubble.model.bill.Promotion;
import bubble.service.bill.PromotionService;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class AccountPromotionsResource {

    @Autowired private PromotionService promoService;

    private Account account;

    public AccountPromotionsResource (Account account) { this.account = account; }

    @GET
    public Response listPromotions(@Context Request req,
                                   @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(account.getUuid())) return forbidden();
        return ok(promoService.listPromosForAccount(account.getUuid()));
    }

    @PUT
    public Response adminAddPromotion(@Context Request req,
                                      @Context ContainerRequest ctx,
                                      Promotion request) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        return ok(promoService.adminAddPromotion(account, request));
    }

    @DELETE @Path("/{id}")
    public Response adminRemovePromotion(@Context Request req,
                                         @Context ContainerRequest ctx,
                                         @PathParam("id") String id) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        return ok(promoService.adminRemovePromotion(account, new Promotion(id)));
    }

}
