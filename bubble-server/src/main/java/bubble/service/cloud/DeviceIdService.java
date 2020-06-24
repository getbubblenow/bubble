package bubble.service.cloud;

import bubble.model.device.Device;

import java.util.List;

public interface DeviceIdService {

    Device findDeviceByIp(String ip);

    List<String> findIpsByDevice(String deviceUuid);

    void initDeviceSecurityLevels();

    void setDeviceSecurityLevel(Device device);

}
