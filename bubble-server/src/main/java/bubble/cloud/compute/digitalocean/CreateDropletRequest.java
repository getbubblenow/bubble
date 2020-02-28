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
public class CreateDropletRequest {

    @Getter @Setter private String name;
    @Getter @Setter private String region;
    @Getter @Setter private String size;
    @Getter @Setter private String image;
    @Getter @Setter private Integer[] ssh_keys;
    @Getter @Setter private boolean backups = false;
    @Getter @Setter private boolean ipv6 = true;
    @Getter @Setter private boolean monitoring = false;
    @Getter @Setter private boolean private_networking = false;
    @Getter @Setter private String user_data;
    @Getter @Setter private String[] volumes;
    @Getter @Setter private String[] tags;

}
