package bubble.service_dbfilter;

import bubble.model.device.Device;
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

}
