package bubble.rule.analytics;

import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.resources.stream.FilterMatchersRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.glassfish.grizzly.http.server.Request;

import static bubble.ApiConstants.getRemoteHost;
import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@NoArgsConstructor
public class TrafficRecord {

    @Getter @Setter private String uuid = randomUUID().toString();
    @Getter @Setter private long requestTime = now();
    @Getter @Setter private String accountUuid;
    @Getter @Setter private String accountName;
    @Getter @Setter private String deviceUuid;
    @Getter @Setter private String deviceName;
    @Getter @Setter private String ip;
    @Getter @Setter private String fqdn;
    @Getter @Setter private String uri;
    @Getter @Setter private String userAgent;

    public TrafficRecord(FilterMatchersRequest filter, Account account, Device device, Request req) {
        setAccountName(account == null ? null : account.getName());
        setAccountUuid(account == null ? null : account.getUuid());
        setDeviceName(device == null ? null : device.getName());
        setDeviceUuid(device == null ? null : device.getUuid());
        setIp(getRemoteHost(req));
        setFqdn(filter.getFqdn());
        setUri(filter.getUri());
        setUserAgent(filter.getUserAgent());
    }
}
