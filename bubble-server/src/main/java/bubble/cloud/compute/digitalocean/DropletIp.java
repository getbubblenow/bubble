/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.compute.digitalocean;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class DropletIp {

    @Getter @Setter private String ip_address;
    @Getter @Setter private String netmask;
    @Getter @Setter private String gateway;
    @Getter @Setter private String type;

}
