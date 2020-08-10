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
import bubble.service.cloud.DeviceIdService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.io.File;
import java.util.List;
import java.util.Optional;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.model.device.Device.UNINITIALIZED_DEVICE_LIKE;
import static bubble.model.device.Device.newUninitializedDevice;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.touch;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.system.Sleep.sleep;

@Repository @Slf4j
public class DeviceDAO extends AccountOwnedEntityDAO<Device> {

    private static final File VPN_REFRESH_USERS_FILE = new File(HOME_DIR, ".algo_refresh_users");
    private static final short SPARE_DEVICES_PER_ACCOUNT_MAX = 10;
    private static final short SPARE_DEVICES_PER_ACCOUNT_THRESHOLD = 10;
    private static final long DEVICE_INIT_TIMEOUT = MINUTES.toMillis(5);

    @Autowired private BubbleConfiguration configuration;
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

    private static final Object createLock = new Object();

    @Transactional
    @Override public Device create(@NonNull final Device device) {
        if (isRawMode() || device.uninitialized() || device.getDeviceType().isNonVpnDevice() || configuration.isSage()) {
            return super.create(device);
        }

        synchronized (createLock) {
            device.initDeviceType();

            final var accountUuid = device.getAccount();
            var uninitializedDevices = findByAccountAndUninitialized(accountUuid);

            if (uninitializedDevices.size() <= SPARE_DEVICES_PER_ACCOUNT_THRESHOLD
                    && !configuration.getBean(AccountDAO.class).findByUuid(accountUuid).isRoot()) {
                if (ensureAllSpareDevices(accountUuid, device.getNetwork())) refreshVpnUsers();
            }

            final Device result;
            uninitializedDevices = findByAccountAndUninitialized(accountUuid);
            if (uninitializedDevices.isEmpty()) {
                log.warn("create: no uninitialized devices for account " + accountUuid);
                // just create the device now:
                device.initTotpKey();
                result = super.create(device);

            } else {
                final Device uninitialized;
                Optional<Device> availableDevice = uninitializedDevices.stream().filter(Device::configsOk).findAny();
                final long start = now();
                while (availableDevice.isEmpty() && now() - start < DEVICE_INIT_TIMEOUT) {
                    if (configuration.testMode()) {
                        log.warn("create: no available uninitialized devices and in test mode, using first uninitialized device...");
                        availableDevice = Optional.of(uninitializedDevices.get(0));
                    } else {
                        // wait for configs to be ok
                        log.warn("create: no available uninitialized devices, waiting...");
                        sleep(SECONDS.toMillis(5), "waiting for available uninitialized device");
                        availableDevice = uninitializedDevices.stream().filter(Device::configsOk).findAny();
                    }
                }
                if (availableDevice.isEmpty()) return die("create: timeout waiting for available uninitialized device");
                uninitialized = availableDevice.get();
                copy(uninitialized, device);
                result = super.update(uninitialized);
            }

            deviceIdService.setDeviceSecurityLevel(result);
            return result;
        }
    }

    @Override @NonNull public Device update(@NonNull final Device updateRequest) {
        final var toUpdate = (Device) readOnlySession().get(Device.class, updateRequest.getUuid());
        if (toUpdate == null) die("Cannot find device to update with uuid: " + updateRequest.getUuid());
        if (toUpdate.uninitialized()) die("Cannot update special devices: " + updateRequest.getName());

        toUpdate.update(updateRequest);
        final var updated = super.update(toUpdate);
        deviceIdService.setDeviceSecurityLevel(updated);
        refreshVpnUsers();
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

    @Override public void forceDelete(String uuid) {
        dataDAO.deleteDevice(uuid);
        super.delete(uuid);
        refreshVpnUsers();
    }

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
