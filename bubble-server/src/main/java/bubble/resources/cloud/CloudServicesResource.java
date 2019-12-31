package bubble.resources.cloud;

import bubble.cloud.CloudServiceType;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.account.Account;
import bubble.model.cloud.CloudService;
import bubble.resources.account.AccountOwnedResource;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import java.util.List;
import java.util.Map;

import static bubble.ApiConstants.EP_DATA;
import static bubble.ApiConstants.EP_STORAGE;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.URIUtil.queryParams;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

public class CloudServicesResource extends AccountOwnedResource<CloudService, CloudServiceDAO> {

    public static final String PARAM_TYPE = "type";

    public CloudServicesResource(Account account) { super(account); }

    @Override protected Object daoCreate(CloudService cloud) {
        try {
            cloud.wireAndSetup(configuration);
        } catch (Exception e) {
            throw invalidEx("err.driverConfig.initFailure");
        }
        return super.daoCreate(cloud);
    }

    @Override protected List<CloudService> list(Request req, ContainerRequest ctx) {
        final Map<String, String> queryParams = queryParams(req.getQueryString());
        final String type = queryParams.get("type");
        if (!empty(type)) {
            final CloudServiceType csType;
            try {
                csType = CloudServiceType.fromString(queryParams.get(PARAM_TYPE));
            } catch (Exception e) {
                throw invalidEx("err.cloudServiceType.invalid", "Not a valid cloud service type: "+type, type);
            }
            return getDao().findByAccountAndType(getAccountUuid(ctx), csType);
        }
        return super.list(req, ctx);
    }

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
