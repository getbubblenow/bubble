package bubble.resources.bill;

import bubble.cloud.CloudServiceType;
import bubble.dao.account.AccountDAO;
import bubble.dao.bill.PromotionDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.bill.Promotion;
import bubble.model.cloud.CloudService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.PROMOTIONS_ENDPOINT;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

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
    public Response listPromos(@Context ContainerRequest ctx,
                               @QueryParam("code") String code) {
        final Account caller = optionalUserPrincipal(ctx);
        return ok(promotionDAO.findEnabledAndNoCodeOrWithCode(code));
    }

    @GET @Path("/{id}")
    public Response findPromo(@Context ContainerRequest ctx,
                              @PathParam("id") String id) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) return forbidden();
        if (!caller.getUuid().equals(getFirstAdmin().getUuid())) return forbidden();

        return ok(promotionDAO.findById(id));
    }

    @PUT
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
