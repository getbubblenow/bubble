/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.cloud;

import lombok.Getter;
import lombok.Setter;

public class ForkRequest {

    @Getter @Setter private String fqdn;
    @Getter @Setter private String adminEmail;
    @Getter @Setter private String cloud;
    @Getter @Setter private String region;
    @Getter @Setter private Boolean exactRegion;

}
