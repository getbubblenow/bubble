/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.stream;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.ArrayUtils;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class FilterConnCheckRequest {

    @Getter @Setter private String clientAddr;
    public boolean hasClientAddr() { return !empty(clientAddr); }

    @Getter @Setter private String serverAddr;
    public boolean hasServerAddr() { return !empty(serverAddr); }

    @Getter @Setter private String[] fqdns;
    public boolean hasFqdns() { return !empty(fqdns); }
    public boolean hasFqdn(String f) { return hasFqdns() && ArrayUtils.contains(fqdns, f); }

}
