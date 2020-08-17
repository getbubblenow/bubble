/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.model.device.DeviceStatus;

import java.util.List;

public interface DeviceIdService {

    Device findDeviceByIp(String ip);

    List<String> findIpsByDevice(String deviceUuid);

    void initDeviceSecurityLevels();
    void setDeviceSecurityLevel(Device device);

    void initBlockStats (Account account);
    default boolean doShowBlockStats(String accountUuid) { return false; }

    DeviceStatus getDeviceStatus(String deviceUuid);
    DeviceStatus getLiveDeviceStatus(String deviceUuid);

}
