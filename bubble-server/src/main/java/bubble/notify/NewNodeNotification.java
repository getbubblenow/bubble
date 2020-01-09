package bubble.notify;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class NewNodeNotification {

    @Getter @Setter private String uuid = randomUUID().toString();
    @Getter @Setter private String account;
    @Getter @Setter private String host;
    @Getter @Setter private String network;
    @Getter @Setter private String networkName;
    @Getter @Setter private String domain;
    @Getter @Setter private String fqdn;
    @Getter @Setter private String cloud;
    @Getter @Setter private String region;
    @Getter @Setter private Boolean automated;
    public boolean automated () { return automated != null && automated; }

    @Getter @Setter private Boolean fork;
    public boolean fork() { return fork != null && fork; }

    @Getter @Setter private String restoreKey;
    public boolean hasRestoreKey () { return !empty(restoreKey); }

    @Getter @Setter private String lock;

}
