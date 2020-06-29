/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.stream;

import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class FilterConnCheckRequest {

    @Getter @Setter private String addr;
    public boolean hasAddr() { return !empty(addr); }

    @Getter @Setter private String[] fqdns;
    public boolean hasFqdns() { return !empty(fqdns); }

    @Getter @Setter private String remoteAddr;
    public boolean hasRemoteAddr() { return !empty(remoteAddr); }

}
