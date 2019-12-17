package bubble.dao.cloud;

import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.cloud.CloudService;
import bubble.cloud.CloudServiceType;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CloudServiceDAO extends AccountOwnedTemplateDAO<CloudService> {

    @Override public Order getDefaultSortOrder() { return Order.desc("priority"); }

    public List<CloudService> findPublicTemplatesByType(String accountUuid, CloudServiceType csType) {
        return findByFields("account", accountUuid, "type", csType, "enabled", true, "template", true);
    }

    public List<CloudService> findByAccountAndType(String accountUuid, CloudServiceType csType) {
        return findByFields("account", accountUuid, "type", csType, "enabled", true);
    }

    public List<CloudService> findByType(CloudServiceType csType) { return findByField("type", csType); }

    public List<CloudService> findByName(String name) { return findByField("name", name); }

    public CloudService findByAccountAndName(String accountUuid, String name) {
        return findByUniqueFields("account", accountUuid, "name", name);
    }

    public CloudService findByAccountAndTypeAndId(String accountUuid, CloudServiceType csType, String id) {
        final CloudService found = findByUniqueFields("account", accountUuid, "type", csType, "enabled", true, "uuid", id);
        return found != null ? found : findByUniqueFields("account", accountUuid, "type", csType, "enabled", true, "name", id);
    }

    public List<CloudService> findByAccountAndTypeAndDriverClass(String accountUuid, CloudServiceType csType, String driverClass) {
        return findByFields("account", accountUuid, "type", csType, "enabled", true, "driverClass", driverClass);
    }

    public CloudService findFirstByAccountAndTypeAndDriverClass(String accountUuid, CloudServiceType csType, String driverClass) {
        final List<CloudService> found = findByAccountAndTypeAndDriverClass(accountUuid, csType, driverClass);
        return found.isEmpty() ? null : found.get(0);
    }

}
