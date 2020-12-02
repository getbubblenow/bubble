/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.bill;

import bubble.cloud.CloudServiceType;
import bubble.dao.account.AccountDAO;
import bubble.dao.bill.PromotionDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.bill.Promotion;
import bubble.model.cloud.CloudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.API_TAG_PAYMENT;
import static bubble.ApiConstants.PROMOTIONS_ENDPOINT;
import static bubble.server.BubbleConfiguration.getDEFAULT_LOCALE;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.util.string.LocaleUtil.currencyForLocale;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(PROMOTIONS_ENDPOINT)
@Service @Slf4j
public class PromotionsResource {

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private PromotionDAO promotionDAO;
    @Autowired private AccountDAO accountDAO;

    @Getter(lazy=true) private final Account firstAdmin = accountDAO.getFirstAdmin();

    @GET
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_PAYMENT,
            summary="List all promotions",
            description="List all promotions. If caller is admin, every defined promotion is returned. If caller is non-admin, then only promotions visible to the caller are returned",
            responses=@ApiResponse(responseCode=SC_OK, description="a JSON array of Promotion objects")
    )
    public Response listPromos(@Context ContainerRequest ctx,
                               @QueryParam("currency") String currency,
                               @QueryParam("code") String code) {
        if (empty(currency)) currency = currencyForLocale(getDEFAULT_LOCALE());
        final Account caller = optionalUserPrincipal(ctx);
        if (caller != null && caller.admin()) {
            return ok(promotionDAO.findAll());
        }
        return ok(promotionDAO.findVisibleAndEnabledAndActiveWithNoCodeOrWithCode(code, currency));
    }

    @GET @Path("/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_PAYMENT,
            summary="Find a promotion by ID",
            description="Find a promotion by ID",
            parameters=@Parameter(name="id", description="UUID or name of promotion", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="a Promotion object"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="no promotion found for ID given"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller is not an admin")
            }
    )
    public Response findPromo(@Context ContainerRequest ctx,
                              @PathParam("id") String id) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        if (!caller.getUuid().equals(getFirstAdmin().getUuid())) return forbidden();

        final Promotion promo = promotionDAO.findById(id);
        return promo == null ? notFound(id) : ok(promo);
    }

    @PUT
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_PAYMENT,
            summary="Create a promotion",
            description="Create a promotion. Must be admin.",
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the Promotion that was created"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller is not an admin")
            }
    )
    public Response createPromo(@Context ContainerRequest ctx,
                                Promotion request) {

        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        if (!caller.getUuid().equals(getFirstAdmin().getUuid())) return forbidden();

        final Promotion existing = promotionDAO.findByName(request.getName());
        if (existing != null) {
            return ok(promotionDAO.update((Promotion) existing.update(request)));
        } else {
            final CloudService cloud = cloudDAO.findByAccountAndTypeAndId(getFirstAdmin().getUuid(), CloudServiceType.payment, request.getCloud());
            if (cloud == null) return notFound(request.getCloud());
            return ok(promotionDAO.create(request.setCloud(cloud.getUuid())));
        }
    }

    @POST @Path("/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_PAYMENT,
            summary="Update a promotion by ID",
            description="Update a promotion by ID",
            parameters=@Parameter(name="id", description="UUID or name of promotion", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the updated Promotion object"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="no promotion found for ID given"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller is not an admin")
            }
    )
    public Response updatePromo(@Context ContainerRequest ctx,
                                 @PathParam("id") String id,
                                 Promotion request) {

        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        if (!caller.getUuid().equals(getFirstAdmin().getUuid())) return forbidden();

        final Promotion existing = promotionDAO.findById(id);
        if (existing == null) return notFound(id);
        return ok(promotionDAO.update((Promotion) existing.update(request)));
    }

    @DELETE @Path("/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_PAYMENT,
            summary="Delete a promotion by ID",
            description="Delete a promotion by ID. Must be admin.",
            parameters=@Parameter(name="id", description="UUID or name of promotion", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="an empty JSON object is returned upon successful deletion"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="no promotion found for ID given"),
                    @ApiResponse(responseCode=SC_FORBIDDEN, description="caller is not an admin")
            }
    )
    public Response deletePromo(@Context ContainerRequest ctx,
                                 @PathParam("id") String id) {

        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        if (!caller.getUuid().equals(getFirstAdmin().getUuid())) return forbidden();

        final Promotion existing = promotionDAO.findById(id);
        if (existing == null) return notFound(id);
        promotionDAO.delete(existing.getUuid());

        return ok_empty();
    }

}
