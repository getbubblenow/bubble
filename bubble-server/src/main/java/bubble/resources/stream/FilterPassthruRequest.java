package bubble.resources.stream;

import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class FilterPassthruRequest {

    @Getter @Setter private String addr;
    public boolean hasAddr() { return !empty(addr); }

    @Getter @Setter private String fqdn;
    public boolean hasFqdn() { return !empty(fqdn); }

    @Getter @Setter private String remoteAddr;
    public boolean hasRemoteAddr() { return !empty(remoteAddr); }

}