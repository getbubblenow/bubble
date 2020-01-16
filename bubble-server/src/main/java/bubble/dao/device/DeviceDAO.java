package bubble.dao.device;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.device.Device;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.List;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.model.device.Device.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.touch;

@Repository @Slf4j
public class DeviceDAO extends AccountOwnedEntityDAO<Device> {

    public static final File VPN_REFRESH_USERS_FILE = new File(HOME_DIR, ".algo_refresh_users");

    @Override public Order getDefaultSortOrder() { return Order.asc("ctime"); }

    public Device findByAccountAndName(String accountUuid, String name) {
        return findByUniqueFields("account", accountUuid, "name", name);
    }

    public Device findByNetworkAndName(String networkUuid, String name) {
        return findByUniqueFields("network", networkUuid, "name", name);
    }

    @Override public Device create(Device device) {
        if (!device.uninitialized()) {
            final String account = device.getAccount();
            final String network = device.getNetwork();
            final List<Device> devices = findByAccountAndUninitialized(account);

            // if we have an uninitialized device, use that, otherwise create a new uninitialized device and use that
            final Device uninitialized;
            if (devices.isEmpty()) {
                uninitialized = ensureSpareDevice(account, network, false);
            } else {
                uninitialized = devices.get(0);
            }
            uninitialized.initialize(device);
            final Device updated = update(uninitialized);

            // always ensure we have one spare device
            ensureSpareDevice(account, network, true);
            return updated;
        }
        return super.create(device);
    }

    @Override public Device update(Device device) {
        final Device current = findByUuid(device.getUuid());
        if (current != null && current.uninitialized()) {
            device.initTotpKey();
        }
        final Device updated = super.update(device);
        ensureSpareDevice(device.getAccount(), device.getNetwork(), true);
        return updated;
    }

    @Override public void delete(String uuid) {
        final Device device = findByUuid(uuid);
        if (device != null) {
            super.delete(uuid);
            ensureSpareDevice(device.getAccount(), device.getNetwork(), true);
        }
    }

    @Override public void forceDelete(String uuid) { super.delete(uuid); }

    public Device ensureSpareDevice(String account, String network, boolean refreshVpnUsers) {
        final List<Device> devices = findByAccountAndUninitialized(account);
        Device uninitialized = null;
        if (devices.isEmpty()) {
            log.info("ensureSpareDevice: no uninitialized devices for account " + account + ", creating one");
            uninitialized = create(newUninitializedDevice(network, account));
        }
        if (refreshVpnUsers) refreshVpnUsers();
        return uninitialized;
    }

    public void refreshVpnUsers() {
        log.info("ensureSpareDevice: refreshing VPN users by touching: "+abs(VPN_REFRESH_USERS_FILE));
        touch(VPN_REFRESH_USERS_FILE);
    }

    public List<Device> findByAccountAndUninitialized(String accountUuid) {
        return findByFieldEqualAndFieldLike("account", accountUuid, "name", UNINITIALIZED_DEVICE_LIKE);
    }
}
