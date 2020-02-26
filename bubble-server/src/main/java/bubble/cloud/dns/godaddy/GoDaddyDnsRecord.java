/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.dns.godaddy;

import bubble.model.cloud.BubbleDomain;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsType;

import static org.cobbzilla.util.dns.DnsRecord.OPT_NS_NAME;

@NoArgsConstructor @Accessors(chain=true)
public class GoDaddyDnsRecord {

    public static final GoDaddyDnsRecord[] EMPTY_ARRAY = new GoDaddyDnsRecord[0];

    @Getter @Setter private String data;
    @Getter @Setter private String name;
    @Getter @Setter private Integer ttl;
    @Getter @Setter private DnsType type;
    @Getter @Setter private Integer priority;

    public DnsRecord toDnsRecord(BubbleDomain domain) {
        return (DnsRecord) new DnsRecord()
                .setOption(OPT_NS_NAME, type == DnsType.NS ? data : null)
                .setType(type)
                .setFqdn(domain.ensureDomainSuffix(name.equals("@") ? "" : name))
                .setValue(data);
    }
}
