/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.device;

import bubble.model.account.Account;
import bubble.model.device.BubbleDeviceType;
import bubble.model.device.Device;
import bubble.model.device.DeviceSecurityLevel;
import bubble.model.device.DeviceStatus;

import java.util.List;

public interface DeviceService {

    Device findDeviceByIp(String ip);

    List<String> findIpsByDevice(String deviceUuid);

    void initDeviceSecurityLevels();
    void setDeviceSecurityLevel(Device device);

    void initBlocksAndFlexRoutes(Account account);
    default boolean doShowBlockStats(String accountUuid) { return false; }
    default Boolean doShowBlockStatsForIpAndFqdn(String ip, String fqdn) { return false; }
    default void setBlockStatsForFqdn (Account account, String fqdn, boolean value) {}
    default void unsetBlockStatsForFqdn (Account account, String fqdn) {}

    DeviceStatus getDeviceStatus(String deviceUuid);
    DeviceStatus getLiveDeviceStatus(String deviceUuid);

    default DeviceSecurityLevel getDefaultSecurityLevel(BubbleDeviceType type) { return type.getDefaultSecurityLevel(); }
    default void setDefaultSecurityLevel(BubbleDeviceType type, DeviceSecurityLevel level) {}

}
