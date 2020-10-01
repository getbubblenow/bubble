/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.device;

import bubble.cloud.geoLocation.GeoLocation;
import bubble.service.cloud.GeoService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;

import java.util.List;

import static java.util.concurrent.TimeUnit.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

@NoArgsConstructor @Accessors(chain=true) @ToString(of={"ip", "location"}) @Slf4j
public class DeviceStatus {

    public static final DeviceStatus NO_DEVICE_STATUS = new DeviceStatus();

    @Getter @Setter private String ip;
    public boolean hasIp () { return ip != null; }
    @Getter @Setter private int port;

    @Getter @Setter private List<String> vpnIps;

    @Getter @Setter private GeoLocation location;

    @Getter @Setter private String bytesSent;
    @Getter @Setter private String sentUnits;
    @Getter @Setter private String bytesReceived;
    @Getter @Setter private String receivedUnits;

    @Getter @Setter private Integer lastHandshakeDays;
    @Getter @Setter private Integer lastHandshakeHours;
    @Getter @Setter private Integer lastHandshakeMinutes;
    @Getter @Setter private Integer lastHandshakeSeconds;
    @Getter @Setter private Long lastHandshakeTime;

    public static final String DEVICE_STATUS_PREFIX = "wg_device_status_";
    public static final String DEVICE_STATUS_ENDPOINT_SUFFIX = "_endpoint";
    public static final String DEVICE_STATUS_TRANSFER_SUFFIX = "_transfer";
    public static final String DEVICE_STATUS_HANDSHAKE_SUFFIX = "_latestHandshake";

    public DeviceStatus(RedisService redis,
                        String geoAccount,
                        GeoService geoService,
                        String deviceUuid,
                        List<String> vpnIps) {
        this.vpnIps = vpnIps;
        final String endpoint = redis.get_plaintext(DEVICE_STATUS_PREFIX+deviceUuid+DEVICE_STATUS_ENDPOINT_SUFFIX);
        if (endpoint != null) {
            try {
                final int lastColon = endpoint.lastIndexOf(':');
                if (lastColon != -1) {
                    setIp(endpoint.substring(0, lastColon));
                    setPort(Integer.parseInt(endpoint.substring(lastColon + 1)));
                    if (geoService != null) {
                        try {
                            setLocation(geoService.locate(geoAccount, getIp(), true));
                        } catch (Exception e) {
                            log.error("DeviceStatus: error calling geoService for ip="+getIp()+": "+shortError(e));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("DeviceStatus: error parsing endpoint: "+endpoint+": "+shortError(e));
            }
        }

        final String transfer = redis.get_plaintext(DEVICE_STATUS_PREFIX+deviceUuid+DEVICE_STATUS_TRANSFER_SUFFIX);
        if (transfer != null) {
            try {
                final String[] parts = transfer.split("\\s+");
                if (parts.length != 6) {
                    log.error("DeviceStatus: error parsing transfer: " + transfer);
                } else {
                    if (!parts[2].equals("received,")) throw new IllegalArgumentException("expected 'received,' in parts[2]");
                    if (!parts[5].equals("sent")) throw new IllegalArgumentException("expected 'sent' in parts[5]");
                    setBytesReceived(parts[0]);
                    setReceivedUnits(parseUnits(parts[1]));
                    setBytesSent(parts[3]);
                    setSentUnits(parseUnits(parts[4]));
                }
            } catch (Exception e) {
                log.error("DeviceStatus: error parsing transfer: "+transfer+": "+shortError(e));
            }
        }

        final String handshake = redis.get_plaintext(DEVICE_STATUS_PREFIX+deviceUuid+DEVICE_STATUS_HANDSHAKE_SUFFIX);
        if (handshake == null) {
            if (transfer != null) {
                log.warn("DeviceStatus: transfer found but no handshake info for device "+deviceUuid);
            }
        } else {
            try {
                final String[] parts = handshake.split("\\s+");
                if (!parts[parts.length-1].equals("ago")) {
                    log.error("DeviceStatus: error parsing handshake, expected 'ago' as last token");
                } else {
                    for (int i=0; i<parts.length-1; i+=2) {
                        final int count = Integer.parseInt(parts[i]);
                        String unit = parts[i+1].trim();
                        if (unit.endsWith(",")) unit = unit.substring(0, unit.length()-1);
                        switch (unit) {
                            case "day": case "days": setLastHandshakeDays(count); break;
                            case "hour": case "hours": setLastHandshakeHours(count); break;
                            case "minute": case "minutes": setLastHandshakeMinutes(count); break;
                            case "second": case "seconds": setLastHandshakeSeconds(count); break;
                            default: throw new IllegalArgumentException("DeviceStatus: error parsing handshake, invalid unit: "+unit);
                        }
                    }
                    initLastHandshakeTime();
                }
            } catch (Exception e) {
                log.error("DeviceStatus: error parsing handshake: "+handshake+": "+shortError(e));
            }
        }
    }

    private String parseUnits(String units) {
        if (units.equalsIgnoreCase("b")) return "b";
        return units.substring(0, 1).toUpperCase();
    }

    private void initLastHandshakeTime() {
        setLastHandshakeTime(now()
                - (getLastHandshakeHours() == null ? 0 : HOURS.toMillis(getLastHandshakeHours()))
                - (getLastHandshakeMinutes() == null ? 0 : MINUTES.toMillis(getLastHandshakeMinutes()))
                - (getLastHandshakeSeconds() == null ? 0 : SECONDS.toMillis(getLastHandshakeSeconds())));
    }
}
