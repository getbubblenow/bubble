package bubble.dao.cloud;

import bubble.cloud.CloudServiceType;
import bubble.cloud.storage.local.LocalStorageDriver;
import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static bubble.ApiConstants.ROOT_NETWORK_UUID;
import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE;
import static bubble.model.cloud.CloudService.testDriver;
import static org.cobbzilla.wizard.model.Identifiable.UUID;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository
public class CloudServiceDAO extends AccountOwnedTemplateDAO<CloudService> {

    @Autowired private AccountDAO accountDAO;
    @Autowired private BubbleConfiguration configuration;

    @Override public Order getDefaultSortOrder() { return Order.desc("priority"); }

    @Override public Object preCreate(CloudService cloud) {
        if (cloud.getType() == CloudServiceType.storage) {
            if (cloud.getName().equals(LOCAL_STORAGE) && !cloud.getDriver().getClass().equals(LocalStorageDriver.class)) {
                throw invalidEx("err.cloud.localStorageIsReservedName");
            } else if (cloud.isNotLocalStorage()) {
                final BubbleNetwork thisNetwork = configuration.getThisNetwork();
                final String networkUuid = thisNetwork == null ? ROOT_NETWORK_UUID : thisNetwork.getUuid();
                if (cloud.hasCredentials() && cloud.getCredentials().needsNewNetworkKey(networkUuid)) {
                    cloud.setCredentials(cloud.getCredentials().initNetworkKey(networkUuid));
                }
            }
        }
        if (cloud.hasCredentials() && cloud.getCredentialsJson().contains("{{") && cloud.getCredentialsJson().contains("}}")) {
            cloud.setCredentialsJson(configuration.applyHandlebars(cloud.getCredentialsJson()));
        }
        if (cloud.hasDriverConfig() && cloud.getDriverConfigJson().contains("{{") && cloud.getDriverConfigJson().contains("}}")) {
            cloud.setDriverConfigJson(configuration.applyHandlebars(cloud.getDriverConfigJson()));
        }
        return super.preCreate(cloud);
    }

    @Override public CloudService postCreate(CloudService cloud, Object context) {
        if (!cloud.delegated() && !configuration.testMode()) {
            final ValidationResult errors = testDriver(cloud, configuration);
            if (errors.isInvalid()) throw invalidEx(errors);
        }
        if (cloud.getType() == CloudServiceType.payment
                && cloud.template()
                && cloud.enabled()
                && !configuration.paymentsEnabled()) {
            // a public template for a payment cloud has been added, and payments were not enabled -- now they are
            configuration.refreshPublicSystemConfigs();
        }
        return super.postCreate(cloud, context);
    }

    @Override public CloudService postUpdate(CloudService cloud, Object context) {
        CloudService.clearDriverCache(cloud.getUuid());
        return super.postUpdate(cloud, context);
    }

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
        final CloudService found = findByUniqueFields("account", accountUuid, "type", csType, "enabled", true, UUID, id);
        return found != null ? found : findByUniqueFields("account", accountUuid, "type", csType, "enabled", true, "name", id);
    }

    public List<CloudService> findByAccountAndTypeAndDriverClass(String accountUuid, CloudServiceType csType, String driverClass) {
        return findByFields("account", accountUuid, "type", csType, "enabled", true, "driverClass", driverClass);
    }

    public CloudService findFirstByAccountAndTypeAndDriverClass(String accountUuid, CloudServiceType csType, String driverClass) {
        final List<CloudService> found = findByAccountAndTypeAndDriverClass(accountUuid, csType, driverClass);
        return found.isEmpty() ? null : found.get(0);
    }

    public boolean paymentsEnabled() {
        final Account admin = accountDAO.findFirstAdmin();
        if (admin == null) return false;
        return !findPublicTemplatesByType(admin.getUuid(), CloudServiceType.payment).isEmpty();
    }
}
