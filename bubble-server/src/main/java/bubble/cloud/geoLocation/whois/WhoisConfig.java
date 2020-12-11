/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.geoLocation.whois;

import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class WhoisConfig {

    @Getter @Setter private String host;
    public boolean hasHost() { return !empty(host); }

    @Getter @Setter private Integer port;
    public boolean hasPort() { return port != null && port > 0 && port < 65536; }

}
