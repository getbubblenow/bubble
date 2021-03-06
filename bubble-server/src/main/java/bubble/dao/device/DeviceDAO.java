/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.device;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.account.TrustedClientDAO;
import bubble.dao.app.AppDataDAO;
import bubble.model.device.BubbleDeviceType;
import bubble.model.device.Device;
import bubble.server.BubbleConfiguration;
import bubble.service.device.DeviceService;
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
import static org.cobbzilla.util.json.JsonUtil.json;
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
    @Autowired private TrustedClientDAO trustDAO;
    @Autowired private FlexRouterDAO flexRouterDAO;
    @Autowired private DeviceService deviceService;

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
        final String prefix = "create(" + device.getName() + "): ";

        if (isRawMode() || device.uninitialized() || device.getDeviceType().isNonVpnDevice() || configuration.isSage()) {
            if (log.isDebugEnabled()) {
                log.debug(prefix+"creating real device object; isRawMode==" + isRawMode() + " || device.uninitialized()==" + device.uninitialized() + " || device.getDeviceType().isNonVpnDevice()==" + device.getDeviceType().isNonVpnDevice() + " || configuration.isSage()==" + configuration.isSage());
            }
            return super.create(device);
        }

        synchronized (createLock) {
            device.initDeviceType();

            final var accountUuid = device.getAccount();
            var uninitializedDevices = findByAccountAndUninitialized(accountUuid);

            if (uninitializedDevices.size() <= SPARE_DEVICES_PER_ACCOUNT_THRESHOLD
                    && !configuration.getBean(AccountDAO.class).isFirstAdmin(accountUuid)) {
                if (ensureAllSpareDevices(accountUuid, device.getNetwork())) refreshVpnUsers();
            }

            final Device result;
            uninitializedDevices = findByAccountAndUninitialized(accountUuid);
            if (uninitializedDevices.isEmpty()) {
                log.warn(prefix+"creating real device object; no uninitialized devices for account " + accountUuid);
                // just create the device now:
                device.initTotpKey();
                result = super.create(device);

            } else {
                final Device uninitialized;
                Optional<Device> availableDevice = uninitializedDevices.stream().filter(Device::configsOk).findAny();
                final long start = now();
                while (availableDevice.isEmpty() && now() - start < DEVICE_INIT_TIMEOUT) {
                    if (configuration.testMode()) {
                        log.warn(prefix+"no available uninitialized devices and in test mode, using first uninitialized device...");
                        availableDevice = Optional.of(uninitializedDevices.get(0));
                    } else {
                        // wait for configs to be ok
                        log.warn(prefix+"no available uninitialized devices, waiting...");
                        sleep(SECONDS.toMillis(5), "waiting for available uninitialized device");
                        availableDevice = uninitializedDevices.stream().filter(Device::configsOk).findAny();
                    }
                }
                if (availableDevice.isEmpty()) return die(prefix+"timeout waiting for available uninitialized device");
                uninitialized = availableDevice.get();
                copy(uninitialized, device);
                if (log.isDebugEnabled()) log.debug(prefix+"initializing existing device: "+uninitialized.getUuid());
                result = super.update(uninitialized);
            }

            deviceService.setDeviceSecurityLevel(result);
            if (log.isDebugEnabled()) log.debug(prefix+"returning device: "+json(result));
            return result;
        }
    }

    @Override @NonNull public Device update(@NonNull final Device updateRequest) {
        final Device toUpdate = (Device) readOnlySession().get(Device.class, updateRequest.getUuid());
        if (toUpdate == null) die("Cannot find device to update with uuid: " + updateRequest.getUuid());
        if (toUpdate.uninitialized()) die("Cannot update special devices: " + updateRequest.getName());

        toUpdate.update(updateRequest);
        final Device updated = super.update(toUpdate);
        deviceService.setDeviceSecurityLevel(updated);
        refreshVpnUsers();
        return updated;
    }

    @Override public void delete(String uuid) {
        final Device device = findByUuid(uuid);
        if (device != null) {
            if (device.uninitialized()) die("Cannot delete special device: " + device.getName());
            deleteDeviceDependencies(uuid);
            super.delete(uuid);
            refreshVpnUsers();
        }
    }

    @Override public void forceDelete(String uuid) {
        deleteDeviceDependencies(uuid);
        super.delete(uuid);
        refreshVpnUsers();
    }

    private void deleteDeviceDependencies(String uuid) {
        dataDAO.deleteDevice(uuid);
        trustDAO.deleteDevice(uuid);
        flexRouterDAO.deleteDevice(uuid);
    }

    @Transactional
    public synchronized boolean ensureAllSpareDevices(@NonNull final String account, @NonNull final String network) {
        final String prefix = "ensureAllSpareDevices(" + account + "): ";
        if (configuration.isSage()) {
            if (log.isDebugEnabled()) log.debug(prefix+"configuration.isSage == true, returning true without doing anything");
            return true;
        }
        final var currentSpareDevices = findByAccountAndUninitialized(account);
        boolean newDevicesCreated = false;
        for (var i = currentSpareDevices.size(); i < SPARE_DEVICES_PER_ACCOUNT_MAX; i++) {
            if (log.isDebugEnabled()) log.debug(prefix+"creating new uninitialized device...");
            super.create(newUninitializedDevice(network, account));
            newDevicesCreated = true;
        }
        if (log.isDebugEnabled()) log.debug(prefix+"returning newDevicesCreated="+newDevicesCreated);
        return newDevicesCreated;
    }

    /**
     * This refresh should be done only if there was real change in device count. The script that is executed afterwards
     * uses only devices' UUIDs, so this should be called . No need to call this method in any other case.
     */
    public void refreshVpnUsers() {
        if (configuration.isSage()) return;
        log.info("ensureSpareDevice: refreshing VPN users by touching: "+abs(VPN_REFRESH_USERS_FILE));
        touch(VPN_REFRESH_USERS_FILE);
    }

    public List<Device> findByAccountAndUninitialized(String accountUuid) {
        return findByFieldEqualAndFieldLike("account", accountUuid, "name", UNINITIALIZED_DEVICE_LIKE);
    }

}
