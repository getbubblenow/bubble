package bubble.resources.bill;

import bubble.cloud.CloudServiceType;
import bubble.cloud.payment.PaymentServiceDriver;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.bill.PaymentMethodType;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bubble.ApiConstants.PAYMENT_METHODS_ENDPOINT;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;
import static org.cobbzilla.wizard.resources.ResourceUtil.userPrincipal;

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
        final Map<PaymentMethodType, CloudService> paymentServices = new HashMap<>();
        for (CloudService cloud : allPaymentServices) {
            final PaymentServiceDriver paymentDriver = cloud.getPaymentDriver(configuration);
            if (type != null && type != paymentDriver.getPaymentMethodType()) continue;
            if (!paymentServices.containsKey(paymentDriver.getPaymentMethodType())) {
                // set credentials to null, because ResultScrubber cannot scrub inside Maps
                paymentServices.put(paymentDriver.getPaymentMethodType(), cloud.setCredentials(null));
            }
        }
        return ok(paymentServices);
    }

}
