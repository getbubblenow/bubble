/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.dns.route53;

import com.amazonaws.regions.Regions;
import lombok.Getter;
import lombok.Setter;

public class Route53DnsConfig {

    @Getter @Setter private Regions region = Regions.DEFAULT_REGION;

}
