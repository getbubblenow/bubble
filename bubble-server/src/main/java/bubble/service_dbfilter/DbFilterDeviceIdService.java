/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service_dbfilter;

import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.model.device.DeviceStatus;
import bubble.service.cloud.DeviceIdService;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Service
public class DbFilterDeviceIdService implements DeviceIdService {

    @Override public Device findDeviceByIp(String ip) { return notSupported("findDeviceByIp"); }

    @Override public List<String> findIpsByDevice(String deviceUuid) { return notSupported("findIpsByDevice"); }

    @Override public void initDeviceSecurityLevels() { notSupported("initDeviceSecurityLevels"); }
    @Override public void setDeviceSecurityLevel(Device device) { notSupported("setDeviceSecurityLevel"); }

    @Override public void initBlockStats(Account account) { notSupported("initBlockStats"); }

    @Override public DeviceStatus getDeviceStatus(String deviceUuid) { return notSupported("getDeviceStats"); }
    @Override public DeviceStatus getLiveDeviceStatus(String deviceUuid) { return notSupported("getLiveDeviceStatus"); }

}
