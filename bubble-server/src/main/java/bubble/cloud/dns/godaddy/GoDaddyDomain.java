/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.dns.godaddy;

import bubble.model.cloud.BubbleDomain;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class GoDaddyDomain {

    @JsonIgnore @Getter @Setter private String domain;
    @Getter @Setter private GoDaddyDnsRecord[] records;

    public GoDaddyDomain(BubbleDomain domain, GoDaddyDnsRecord[] records) {
        this.domain = domain.getName();
        this.records = records;
    }

}
