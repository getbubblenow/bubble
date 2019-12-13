package bubble.resources.cloud;

import bubble.dao.cloud.CloudServiceDataDAO;
import bubble.model.account.Account;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.CloudServiceData;
import bubble.resources.account.AccountOwnedResource;
import org.glassfish.jersey.server.ContainerRequest;

import java.util.List;

public class CloudServiceDataResource extends AccountOwnedResource<CloudServiceData, CloudServiceDataDAO> {

    private CloudService cloud;

    public CloudServiceDataResource(Account account, CloudService cloud) {
        super(account);
        this.cloud = cloud;
    }

    @Override protected List<CloudServiceData> list(ContainerRequest ctx) {
        return getDao().findByAccountAndCloud(getAccountUuid(ctx), cloud.getUuid());
    }

    @Override protected CloudServiceData find(ContainerRequest ctx, String id) {
        return getDao().findByAccountAndCloudAndId(getAccountUuid(ctx), cloud.getUuid(), id);
    }

    @Override protected CloudServiceData setReferences(ContainerRequest ctx, Account caller, CloudServiceData cloudServiceData) {
        cloudServiceData.setCloud(cloud.getUuid());
        return super.setReferences(ctx, caller, cloudServiceData);
    }

}
