/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule;

import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.resources.stream.FilterMatchersRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@NoArgsConstructor @Accessors(chain=true)
public class TrafficRecord {

    @Getter @Setter private String uuid = randomUUID().toString();
    @Getter @Setter private long requestTime = now();
    @Getter @Setter private String action;
    @Getter @Setter private String accountUuid;
    @Getter @Setter private String accountEmail;
    @Getter @Setter private String deviceUuid;
    @Getter @Setter private String deviceName;
    @Getter @Setter private String ip;
    @Getter @Setter private String fqdn;
    @Getter @Setter private String uri;
    @Getter @Setter private String userAgent;
    @Getter @Setter private String referer;

    public TrafficRecord(FilterMatchersRequest filter, Account account, Device device) {
        setAccountEmail(account == null ? null : account.getEmail());
        setAccountUuid(account == null ? null : account.getUuid());
        setDeviceName(device == null ? null : device.getName());
        setDeviceUuid(device == null ? null : device.getUuid());
        setIp(filter.getRemoteAddr());
        setFqdn(filter.getFqdn());
        setUri(filter.getUri());
        setUserAgent(filter.getUserAgent());
        setReferer(filter.getReferer());
    }

}
