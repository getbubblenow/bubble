/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.dns.godaddy;

import lombok.Getter;
import lombok.Setter;

public class GoDaddyDnsConfig {

    public static final String GODADDY_BASE_URI = "https://api.godaddy.com/v1/domains/";

    @Getter @Setter private String baseUri = GODADDY_BASE_URI;

}
