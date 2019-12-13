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

    @Getter @Setter private String data;
    @Getter @Setter private String name;
    @Getter @Setter private Integer ttl;
    @Getter @Setter private DnsType type;
    @Getter @Setter private Integer priority;

    public DnsRecord toDnsRecord(BubbleDomain domain) {
        return (DnsRecord) new DnsRecord()
                .setOption(OPT_NS_NAME, type == DnsType.NS ? data : null)
                .setType(type)
                .setFqdn((name.equals("@") ? "" : name+".")+domain.getName())
                .setValue(data);
    }
}
