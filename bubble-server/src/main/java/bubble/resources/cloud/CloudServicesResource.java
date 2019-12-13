package bubble.resources.cloud;

import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.CloudService;
import bubble.resources.account.AccountOwnedResource;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;

import static bubble.ApiConstants.EP_DATA;
import static bubble.ApiConstants.EP_STORAGE;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.userPrincipal;

public class CloudServicesResource extends AccountOwnedResource<CloudService, CloudServiceDAO> {

    public CloudServicesResource(Account account) { super(account); }

    @Path("/{id}"+EP_DATA)
    public CloudServiceDataResource getData(@Context ContainerRequest ctx,
                                            @PathParam("id") String id) {
        final Account caller = userPrincipal(ctx);
        final CloudService cloud = find(ctx, id);
        if (cloud == null) throw notFoundEx(id);
        return configuration.subResource(CloudServiceDataResource.class, caller, cloud);
    }

    @Path("/{id}"+EP_STORAGE)
    public StorageResource getStorage(@Context ContainerRequest ctx,
                                      @PathParam("id") String id) {
        final CloudService cloud = find(ctx, id);
        return configuration.subResource(StorageResource.class, account, cloud);
    }

}
