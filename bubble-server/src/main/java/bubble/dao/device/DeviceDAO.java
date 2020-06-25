/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.device;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.app.AppDataDAO;
import bubble.model.device.BubbleDeviceType;
import bubble.model.device.Device;
import bubble.server.BubbleConfiguration;
import lombok.NonNull;
import bubble.service.cloud.DeviceIdService;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.io.File;
import java.util.List;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.model.device.Device.UNINITIALIZED_DEVICE_LIKE;
import static bubble.model.device.Device.newUninitializedDevice;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.touch;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@Repository @Slf4j
public class DeviceDAO extends AccountOwnedEntityDAO<Device> {

    private static final File VPN_REFRESH_USERS_FILE = new File(HOME_DIR, ".algo_refresh_users");
    private static final short SPARE_DEVICES_PER_ACCOUNT_MAX = 10;
    private static final short SPARE_DEVICES_PER_ACCOUNT_THRESHOLD = 5;

    @Autowired private BubbleConfiguration configuration;
    @Autowired private AccountDAO accountDAO;
    @Autowired private AppDataDAO dataDAO;
    @Autowired private DeviceIdService deviceIdService;

    @Override public Order getDefaultSortOrder() { return ORDER_CTIME_ASC; }

    public Device findByAccountAndName(String accountUuid, String name) {
        return findByUniqueFields("account", accountUuid, "name", name);
    }

    public Device findByNetworkAndName(String networkUuid, String name) {
        return findByUniqueFields("network", networkUuid, "name", name);
    }

    @Override public Object preCreate(Device device) {
        if (device.uninitialized()) {
            device.setDeviceType(BubbleDeviceType.uninitialized);
        }
        return super.preCreate(device);
    }

    @Transactional
    @Override public Device create(@NonNull final Device device) {
        if (device.uninitialized()) return super.create(device);
        device.initDeviceType();

        final var accountUuid = device.getAccount();
        final var uninitializedDevices = findByAccountAndUninitialized(accountUuid);

        var newDevicesCreated = false;
        if (uninitializedDevices.size() <= SPARE_DEVICES_PER_ACCOUNT_THRESHOLD
                && !configuration.getBean(AccountDAO.class).findByUuid(accountUuid).isRoot()) {
            newDevicesCreated = ensureAllSpareDevices(accountUuid, device.getNetwork());
        }

        final Device result;
        // run the above creation of spare devices in parallel, but if there were no spare devices loaded before that,
        // create a brand new entry here:
        if (uninitializedDevices.isEmpty()) {
            log.info("create: no uninitialized devices for account " + accountUuid);
            // just create the device now:
            device.initTotpKey();
            result = super.create(device);
            newDevicesCreated = true;
        } else {
            final var uninitialized = uninitializedDevices.get(0);
            copy(uninitialized, device);
            result = super.update(uninitialized);
        }

        if (newDevicesCreated) refreshVpnUsers();
        return result;
    }

    @Override @NonNull public Device update(@NonNull final Device updateRequest) {
        final var toUpdate = (Device) readOnlySession().get(Device.class, updateRequest.getUuid());
        if (toUpdate == null) die("Cannot find device to update with uuid: " + updateRequest.getUuid());
        if (toUpdate.uninitialized()) die("Cannot update special devices: " + updateRequest.getName());

        toUpdate.update(updateRequest);
        final var updated = super.update(toUpdate);
        deviceIdService.setDeviceSecurityLevel(updated);
        return updated;
    }

    @Override public void delete(String uuid) {
        final Device device = findByUuid(uuid);
        if (device != null) {
            if (device.uninitialized()) die("Cannot delete special device: " + device.getName());

            dataDAO.deleteDevice(uuid);
            super.delete(uuid);
            refreshVpnUsers();
        }
    }

    @Override public void forceDelete(String uuid) { super.delete(uuid); }

    @Transactional
    public synchronized boolean ensureAllSpareDevices(@NonNull final String account, @NonNull final String network) {
        final var currentSpareDevices = findByAccountAndUninitialized(account);
        boolean newDevicesCreated = false;
        for (var i = currentSpareDevices.size(); i < SPARE_DEVICES_PER_ACCOUNT_MAX; i++) {
            super.create(newUninitializedDevice(network, account));
            newDevicesCreated = true;
        }
        return newDevicesCreated;
    }

    /**
     * This refresh should be done only if there was real change in device count. The script that is executed afterwards
     * uses only devices' UUIDs, so this should be called . No need to call this method in any other case.
     */
    public void refreshVpnUsers() {
        log.info("ensureSpareDevice: refreshing VPN users by touching: "+abs(VPN_REFRESH_USERS_FILE));
        touch(VPN_REFRESH_USERS_FILE);
    }

    public List<Device> findByAccountAndUninitialized(String accountUuid) {
        return findByFieldEqualAndFieldLike("account", accountUuid, "name", UNINITIALIZED_DEVICE_LIKE);
    }

}
