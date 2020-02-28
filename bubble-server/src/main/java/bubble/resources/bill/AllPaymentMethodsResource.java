/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.resources.bill;

import bubble.cloud.CloudServiceType;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.bill.PaymentMethodType;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.PaymentService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.*;

import static bubble.ApiConstants.PAYMENT_METHODS_ENDPOINT;
import static org.cobbzilla.util.collection.HasPriority.SORT_PRIORITY;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(PAYMENT_METHODS_ENDPOINT)
@Service @Slf4j
public class AllPaymentMethodsResource {

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    @GET
    public Response listPaymentMethods(@Context ContainerRequest ctx,
                                       @QueryParam("type") PaymentMethodType type) {
        final Account account = userPrincipal(ctx);
        final List<CloudService> allPaymentServices = cloudDAO.findByAccountAndType(account.getUuid(), CloudServiceType.payment);
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
