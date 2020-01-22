package bubble.service.cloud;

import bubble.dao.account.AccountDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.device.Device;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.FilenamePrefixFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static bubble.ApiConstants.HOME_DIR;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Service @Slf4j
public class DeviceIdService {

    public static final File WG_DEVICES_DIR = new File(HOME_DIR, "wg_devices");

    public static final String IP_FILE_PREFIX = "ip_";
    public static final FilenamePrefixFilter IP_FILE_FILTER = new FilenamePrefixFilter(IP_FILE_PREFIX);

    public static final String DEVICE_FILE_PREFIX = "device_";
    public static final FilenamePrefixFilter DEVICE_FILE_FILTER = new FilenamePrefixFilter(DEVICE_FILE_PREFIX);

    @Autowired private DeviceDAO deviceDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private AccountDAO accountDAO;

    private Map<String, Device> deviceCache = new ExpirationMap<>(MINUTES.toMillis(10));

    public Device findDeviceByIp (String ipAddr) {

        if (!WG_DEVICES_DIR.exists()) {
            if (configuration.testMode() && ipAddr.equals("127.0.0.1")) {
                // this is a test
                return new Device().setAccount(accountDAO.getFirstAdmin().getUuid());
            }
            throw invalidEx("err.deviceDir.notFound");
        }

        return deviceCache.computeIfAbsent(ipAddr, ip -> {
            try {
                // try the simple case first
                final File ipFile = new File(WG_DEVICES_DIR, "ip_" + ip);
                if (ipFile.exists() && ipFile.length() > 0) {
                    final String deviceUuid = FileUtil.toString(ipFile).trim();
                    return deviceDAO.findByUuid(deviceUuid);
                }

                // walk through each ip file, finding one whose name semantically matches the given IP
                // this may be required for IPv6 since there can be multiple string representation of the same IP
                final String[] ipFiles = WG_DEVICES_DIR.list(IP_FILE_FILTER);
                if (ipFiles == null || ipFile.length() == 0) return null;

                final InetAddress addr = InetAddress.getByName(ip);
                for (String f : ipFiles) {
                    try {
                        if (InetAddress.getByName(f.substring(IP_FILE_PREFIX.length())).equals(addr)) {
                            final String deviceUuid = FileUtil.toString(new File(WG_DEVICES_DIR, f)).trim();
                            return deviceDAO.findByUuid(deviceUuid);
                        }
                    } catch (Exception e) {
                        log.warn("findDeviceByIp("+ip+"): error handling IP file: "+f+": "+shortError(e));
                    }
                }
                log.warn("findDeviceByIp("+ip+"): no devices found for IP: "+ip);
                return null;

            } catch (IOException e) {
                return die("findDeviceByIp("+ip+") error: "+e, e);
            }
        });
    }

    public List<String> findIpsByDevice(String deviceUuid) {
        if (!WG_DEVICES_DIR.exists()) throw invalidEx("err.deviceDir.notFound");
        final File deviceFile = new File(WG_DEVICES_DIR, DEVICE_FILE_PREFIX+deviceUuid);
        if (!deviceFile.exists() || deviceFile.length() == 0) return Collections.emptyList();
        try {
            return FileUtil.toStringList(deviceFile);
        } catch (IOException e) {
            return die("findIpsByDevice("+deviceUuid+") error: "+e, e);
        }
    }

}
