package bubble.resources.cloud;

import bubble.cloud.CloudServiceType;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.CloudService;
import bubble.resources.account.AccountOwnedTemplateResource;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.stereotype.Service;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Arrays;
import java.util.List;

import static bubble.ApiConstants.CLOUDS_ENDPOINT;
import static bubble.ApiConstants.EP_DATA;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Path(CLOUDS_ENDPOINT)
@Service @Slf4j
public class PublicCloudServicesResource extends AccountOwnedTemplateResource<CloudService, CloudServiceDAO> {

    public PublicCloudServicesResource() { super(null); }

    @Path("/{id}"+EP_DATA)
    public CloudServiceDataResource getData(@Context ContainerRequest ctx,
                                            @PathParam("id") String id) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) throw forbiddenEx();
        final CloudService cloud = find(ctx, id);
        if (cloud == null) throw notFoundEx(id);
        return configuration.subResource(CloudServiceDataResource.class, caller, cloud);
    }

    @Override protected List<CloudService> list(ContainerRequest ctx) {
        final MultivaluedMap<String, String> params = ctx.getUriInfo().getQueryParameters();
        if (params.isEmpty()) return super.list(ctx);

        final String type = params.getFirst("type");
        if (type == null) return super.list(ctx);

        final CloudServiceType csType;
        try {
            csType = CloudServiceType.valueOf(type);
        } catch (Exception e) {
            throw invalidEx("err.cloudType.invalid", "cloud type was invalid, use one of: "+Arrays.toString(CloudServiceType.values()), type);
        }

        return getDao().findPublicTemplatesByType(getAccountUuid(ctx), csType);
    }
}
