/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.account;

import bubble.model.account.Account;
import bubble.model.bill.Promotion;
import bubble.service.bill.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class AccountPromotionsResource {

    @Autowired private PromotionService promoService;

    private final Account account;

    public AccountPromotionsResource (Account account) { this.account = account; }

    @GET
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            summary="List promotions for account",
            description="List promotions for account",
            responses={@ApiResponse(responseCode=SC_OK, description="an array of Promotion objects owned by the Account")}
    )
    public Response listPromotions(@Context Request req,
                                   @Context ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(account.getUuid())) return forbidden();
        return ok(promoService.listPromosForAccount(account.getUuid()));
    }

    @PUT
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            summary="Add a promotion to an account. Must be admin.",
            description="Add a promotion to an account. Must be admin.",
            responses={@ApiResponse(responseCode=SC_OK, description="an array of Promotion objects owned by the Account")}
    )
    public Response adminAddPromotion(@Context Request req,
                                      @Context ContainerRequest ctx,
                                      Promotion request) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        return ok(promoService.adminAddPromotion(account, request));
    }

    @DELETE @Path("/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            summary="Remove a promotion from an account. Must be admin.",
            description="Remove a promotion from an account. Must be admin.",
            responses={@ApiResponse(responseCode=SC_OK, description="an array of Promotion objects owned by the Account")}
    )
    public Response adminRemovePromotion(@Context Request req,
                                         @Context ContainerRequest ctx,
                                         @PathParam("id") String id) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        return ok(promoService.adminRemovePromotion(account, new Promotion(id)));
    }

}
