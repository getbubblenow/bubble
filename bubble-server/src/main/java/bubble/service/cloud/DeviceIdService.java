/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.model.device.Device;

import java.util.List;

public interface DeviceIdService {

    Device findDeviceByIp(String ip);

    List<String> findIpsByDevice(String deviceUuid);

    void initDeviceSecurityLevels();

    void setDeviceSecurityLevel(Device device);

}
