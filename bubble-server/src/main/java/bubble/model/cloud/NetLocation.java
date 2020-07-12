/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

import static org.cobbzilla.util.daemon.ZillaRuntime.bool;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class NetLocation implements Serializable {

    @Getter @Setter private String ip;
    public boolean hasIp () { return !empty(ip); }

    @Getter @Setter private String cloud;
    public boolean hasCloud () { return !empty(cloud); }

    @Getter @Setter private String region;
    public boolean hasRegion () { return !empty(region); }

    @Getter @Setter private Boolean exactRegion;
    public boolean exactRegion () { return bool(exactRegion); }

    public static NetLocation fromCloudAndRegion(String cloud, String region, Boolean exactRegion) {
        return new NetLocation().setCloud(cloud).setRegion(region).setExactRegion(exactRegion);
    }

    public static NetLocation fromIp(String ip) {
        return new NetLocation().setIp(ip);
    }

}
