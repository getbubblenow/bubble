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

}
