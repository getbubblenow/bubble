/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.device;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.AppSiteDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.app.AppSite;
import bubble.model.device.BubbleDeviceType;
import bubble.model.device.Device;
import bubble.model.device.DeviceSecurityLevel;
import bubble.model.device.DeviceStatus;
import bubble.server.BubbleConfiguration;
import bubble.service.cloud.GeoService;
import bubble.service.stream.StandardRuleEngineService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.FilenamePrefixFilter;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.ApiConstants.getPrivateIp;
import static bubble.model.device.DeviceStatus.NO_DEVICE_STATUS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Service @Slf4j
public class StandardDeviceService implements DeviceService {

    public static final File WG_DEVICES_DIR = new File(HOME_DIR, "wg_devices");

    public static final String IP_FILE_PREFIX = "ip_";
    public static final FilenamePrefixFilter IP_FILE_FILTER = new FilenamePrefixFilter(IP_FILE_PREFIX);

    public static final String DEVICE_FILE_PREFIX = "device_";

    public static final String REDIS_DEFAULT_SECURITY_LEVEL_PREFIX = "bubble_default_device_security_level";

    // used in dnscrypt-proxy and mitmproxy to check device security level
    public static final String REDIS_KEY_DEVICE_SECURITY_LEVEL_PREFIX = "bubble_device_security_level_";
    public static final String REDIS_KEY_DEVICE_SITE_MAX_SECURITY_LEVEL_PREFIX = "bubble_device_site_max_security_level_";
    public static final String REDIS_KEY_ACCOUNT_SHOW_BLOCK_STATS = "bubble_account_showBlockStats_";

    // used in dnscrypt-proxy to determine how to respond to blocked requests
    public static final String REDIS_KEY_DEVICE_REJECT_WITH = "bubble_device_reject_with_";

    // used in dnscrypt-proxy to determine how to respond to flex routed requests
    public static final String REDIS_KEY_DEVICE_FLEX_WITH = "bubble_device_flex_with_";

    // used in mitmproxy to determine how to respond to blocked requests
    public static final String REDIS_KEY_DEVICE_SHOW_BLOCK_STATS = "bubble_device_showBlockStats_";

    // used in mitmproxy to optimize passthru requests
    // we flush keys with this prefix when changing showBlockStats flag
    public static final String REDIS_KEY_CHUNK_FILTER_PASS = "__chunk_filter_pass__";

    @Autowired private DeviceDAO deviceDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired private RedisService redis;
    @Autowired private GeoService geoService;
    @Autowired private AppSiteDAO siteDAO;
    @Autowired private StandardRuleEngineService ruleEngine;
    @Autowired private BubbleConfiguration configuration;

    private final Map<String, Device> deviceCache = new ExpirationMap<>(MINUTES.toMillis(10));

    @Getter(lazy=true) private final RedisService defaultSecurityLevelCache = redis.prefixNamespace(REDIS_DEFAULT_SECURITY_LEVEL_PREFIX);

    @Override public DeviceSecurityLevel getDefaultSecurityLevel(BubbleDeviceType type) {
        final String defaultLevel = getDefaultSecurityLevelCache().get(type.name());
        final DeviceSecurityLevel regularDefault = type.getDefaultSecurityLevel();
        if (defaultLevel != null) {
            try {
                return DeviceSecurityLevel.fromString(defaultLevel);
            } catch (Exception e) {
                log.error("getDefaultSecurityLevel("+type+") returned invalid value (using default="+regularDefault+"): "+defaultLevel);
            }
        }
        return regularDefault;
    }

    @Override public void setDefaultSecurityLevel(BubbleDeviceType type, DeviceSecurityLevel level) {
        getDefaultSecurityLevelCache().set(type.name(), level.name());
    }

    @Override public Device findDeviceByIp (String ipAddr) {

        if (!WG_DEVICES_DIR.exists()) {
            if (configuration.testMode()) return findTestDevice(ipAddr);
            reportError("findDeviceByIp: err.deviceDir.notFound");
            return null;
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

    public static final SingletonList<String> TEST_DEVICE_IP_LIST = new SingletonList<>("127.0.0.1");

    @Override public List<String> findIpsByDevice(String deviceUuid) {
        if (!WG_DEVICES_DIR.exists()) {
            if (configuration.testMode()) return TEST_DEVICE_IP_LIST;
            throw invalidEx("err.deviceDir.notFound");
        }
        final File deviceFile = new File(WG_DEVICES_DIR, DEVICE_FILE_PREFIX+deviceUuid);
        if (!deviceFile.exists() || deviceFile.length() == 0) return Collections.emptyList();
        try {
            return FileUtil.toStringList(deviceFile);
        } catch (IOException e) {
            return die("findIpsByDevice("+deviceUuid+") error: "+e, e);
        }
    }

    @Override public void initDeviceSecurityLevels() {
        if (configuration.testMode()) return;
        for (Device device : deviceDAO.findAll()) {
            if (device.uninitialized()) continue;
            setDeviceSecurityLevel(device);
        }
    }

    @Override public void setDeviceSecurityLevel(Device device) {
        if (configuration.testMode()) return;
        for (String ip : findIpsByDevice(device.getUuid())) {
            if (log.isDebugEnabled()) log.debug("setDeviceSecurityLevel("+device.getName()+") setting "+REDIS_KEY_DEVICE_SECURITY_LEVEL_PREFIX+ip+" = "+device.getSecurityLevel().name());
            redis.set_plaintext(REDIS_KEY_DEVICE_SECURITY_LEVEL_PREFIX + ip, device.getSecurityLevel().name());

            for (AppSite site : siteDAO.findByAccount(device.getAccount())) {
                final String siteKey = REDIS_KEY_DEVICE_SITE_MAX_SECURITY_LEVEL_PREFIX + ip;
                if (site.hasMaxSecurityHosts()) {
                    if (site.enableMaxSecurityHosts()) {
                        if (log.isDebugEnabled()) log.debug("setDeviceSecurityLevel("+device.getName()+") adding to "+REDIS_KEY_DEVICE_SITE_MAX_SECURITY_LEVEL_PREFIX+ip+": "+ site.getMaxSecurityHostsJson());
                        redis.sadd_plaintext(siteKey, site.getMaxSecurityHosts());
                    } else {
                        if (log.isDebugEnabled()) log.debug("setDeviceSecurityLevel("+device.getName()+") removing from "+REDIS_KEY_DEVICE_SITE_MAX_SECURITY_LEVEL_PREFIX+ip+": "+ site.getMaxSecurityHostsJson());
                        redis.srem(siteKey, site.getMaxSecurityHosts());
                    }
                }
            }
        }
    }

    @Override public void initBlocksAndFlexRoutes(Account account) {
        final boolean showBlockStats = configuration.showBlockStatsSupported() && account.showBlockStats();
        redis.set_plaintext(REDIS_KEY_ACCOUNT_SHOW_BLOCK_STATS+account.getUuid(), Boolean.toString(showBlockStats));
        redis.del_matching_withPrefix(REDIS_KEY_CHUNK_FILTER_PASS+"*");
        for (Device device : deviceDAO.findByAccount(account.getUuid())) {
            if (showBlockStats) {
                showBlockStats(device);
            } else {
                hideBlockStats(device);
            }
            initFlexRoutes(device);
        }
    }

    @Override public boolean doShowBlockStats(String accountUuid) {
        return configuration.showBlockStatsSupported()
                && Boolean.parseBoolean(redis.get_plaintext(REDIS_KEY_ACCOUNT_SHOW_BLOCK_STATS + accountUuid));
    }

    @Override public Boolean doShowBlockStatsForIpAndFqdn(String ip, String fqdn) {
        if (!configuration.showBlockStatsSupported()) return false;
        while (true) {
            final String val = redis.get_plaintext(REDIS_KEY_DEVICE_SHOW_BLOCK_STATS + ip + ":" + fqdn);
            if (val != null) return Boolean.parseBoolean(val);
            final int dotPos = fqdn.indexOf('.');
            if (dotPos == -1) return null;
            fqdn = fqdn.substring(dotPos + 1);
            if (fqdn.indexOf('.') == -1) return null;
        }
    }

    public void showBlockStats (Device device) {
        final String privateIp = getPrivateIp();
        if (privateIp == null) return;
        for (String ip : findIpsByDevice(device.getUuid())) {
            redis.set_plaintext(REDIS_KEY_DEVICE_SHOW_BLOCK_STATS + ip, Boolean.toString(true));
            redis.set_plaintext(REDIS_KEY_DEVICE_REJECT_WITH + ip, privateIp);
        }
    }

    public void hideBlockStats (Device device) {
        for (String ip : findIpsByDevice(device.getUuid())) {
            redis.del_withPrefix(REDIS_KEY_DEVICE_SHOW_BLOCK_STATS + ip);
            redis.del_withPrefix(REDIS_KEY_DEVICE_REJECT_WITH + ip);
        }
    }

    public void initFlexRoutes (Device device) {
        final String privateIp = getPrivateIp();
        if (privateIp == null) return;
        for (String ip : findIpsByDevice(device.getUuid())) {
            redis.set_plaintext(REDIS_KEY_DEVICE_FLEX_WITH + ip, privateIp);
        }
    }

    @Override public void setBlockStatsForFqdn(Account account, String fqdn, boolean value) {
        for (Device device : deviceDAO.findByAccount(account.getUuid())) {
            for (String ip : findIpsByDevice(device.getUuid())) {
                redis.set_plaintext(REDIS_KEY_DEVICE_SHOW_BLOCK_STATS + ip + ":" + fqdn, String.valueOf(value));
            }
        }
        ruleEngine.flushMatchers();
    }

    @Override public void unsetBlockStatsForFqdn(Account account, String fqdn) {
        for (Device device : deviceDAO.findByAccount(account.getUuid())) {
            for (String ip : findIpsByDevice(device.getUuid())) {
                redis.del_withPrefix(REDIS_KEY_DEVICE_SHOW_BLOCK_STATS + ip + ":" + fqdn);
            }
        }
        ruleEngine.flushMatchers();
    }

    private final ExpirationMap<String, DeviceStatus> deviceStatusCache = new ExpirationMap<>(MINUTES.toMillis(2));

    @Override public DeviceStatus getDeviceStatus(String deviceUuid) {
        return deviceStatusCache.computeIfAbsent(deviceUuid, k -> getLiveDeviceStatus(deviceUuid));
    }

    @Override public DeviceStatus getLiveDeviceStatus(String deviceUuid) {
        if (configuration.testMode()) return NO_DEVICE_STATUS;
        return new DeviceStatus(redis, accountDAO.getFirstAdmin().getUuid(), geoService, deviceUuid);
    }

    private Device findTestDevice(String ipAddr) {
        final String adminUuid = accountDAO.getFirstAdmin().getUuid();
        final List<Device> adminDevices = deviceDAO.findByAccount(adminUuid);
        if (empty(adminDevices)) {
            log.warn("findDeviceByIp("+ipAddr+") test mode and no admin devices, returning dummy device");
            return new Device().setAccount(adminUuid).setName("dummy");
        } else {
            log.warn("findDeviceByIp("+ipAddr+") test mode, returning and possibly initializing first admin device");
            final Device device = adminDevices.get(0);
            return !device.hasTotpKey() ? deviceDAO.update(device.initTotpKey()) : device;
        }
    }

}
