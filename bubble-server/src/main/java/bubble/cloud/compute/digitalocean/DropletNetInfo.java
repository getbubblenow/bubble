/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute.digitalocean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class DropletNetInfo {

    @Getter @Setter private DropletIp[] v4;
    @Getter @Setter private DropletIp[] v6;

    @JsonIgnore public String getIp4() { return empty(v4) ? null : v4[0].getIp_address(); }
    @JsonIgnore public String getIp6() { return empty(v6) ? null : v6[0].getIp_address(); }

}
