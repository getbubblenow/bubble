/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.bill;

import bubble.cloud.CloudServiceType;
import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.bill.PaymentMethodType;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.PaymentService;
import bubble.server.BubbleConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static bubble.ApiConstants.API_TAG_PAYMENT;
import static bubble.ApiConstants.PAYMENT_METHODS_ENDPOINT;
import static org.cobbzilla.util.collection.HasPriority.SORT_PRIORITY;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_NOT_FOUND;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(PAYMENT_METHODS_ENDPOINT)
@Service @Slf4j
public class AllPaymentMethodsResource {

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired private BubbleConfiguration configuration;

    @GET
    @Operation(tags=API_TAG_PAYMENT,
            summary="List all payment methods",
            description="List all payment methods",
            responses=@ApiResponse(responseCode=SC_OK, description="a JSON array of CloudService objects representing supported payment methods")
    )
    public Response listPaymentMethods(@Context ContainerRequest ctx,
                                       @QueryParam("type") PaymentMethodType type) {
        final Account account = optionalUserPrincipal(ctx);
        final List<CloudService> allPaymentServices = account != null
                ? cloudDAO.findByAccountAndType(account.getUuid(), CloudServiceType.payment)
                : cloudDAO.findPublicTemplatesByType(accountDAO.getFirstAdmin().getUuid(), CloudServiceType.payment);
        final Set<PaymentMethodType> typesFound = new HashSet<>();
        final List<CloudService> paymentServices = new ArrayList<>();
        for (CloudService cloud : allPaymentServices) {
            final PaymentMethodType paymentMethodType = cloud.getPaymentDriver(configuration).getPaymentMethodType();
            if (type != null && type != paymentMethodType) continue;
            if (paymentMethodType == PaymentMethodType.promotional_credit) continue; // do not include promotions
            if (!typesFound.contains(paymentMethodType)) {
                paymentServices.add(new PaymentService(cloud, paymentMethodType));
                typesFound.add(paymentMethodType);
            }
        }
        paymentServices.sort(SORT_PRIORITY);
        return ok(paymentServices);
    }

    @GET @Path("/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_PAYMENT,
            summary="Find a payment method",
            description="Find a payment method",
            parameters=@Parameter(name="id", description="UUID or name of CloudService, or name of driver class", required=true),
            responses={
                    @ApiResponse(responseCode=SC_OK, description="a PaymentService object"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description="no payment method found with the given id")
            }
    )
    public Response findPaymentMethod(@Context ContainerRequest ctx,
                                      @PathParam("id") String id) {
        final Account account = userPrincipal(ctx);
        CloudService cloud = cloudDAO.findByAccountAndTypeAndId(account.getUuid(), CloudServiceType.payment, id);
        if (cloud == null) {
            // try to find by driverClass
            cloud = cloudDAO.findFirstByAccountAndTypeAndDriverClass(account.getUuid(), CloudServiceType.payment, id);
        }
        if (cloud == null) return notFound(id);
        if (cloud.getPaymentDriver(configuration).getPaymentMethodType() == PaymentMethodType.promotional_credit) {
            return notFound(id);  // cannot find promotions this way
        }
        return ok(new PaymentService(cloud, cloud.getPaymentDriver(configuration).getPaymentMethodType()));
    }

}
