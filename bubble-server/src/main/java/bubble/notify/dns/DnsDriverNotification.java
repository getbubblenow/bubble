package bubble.notify.dns;

import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.notify.SynchronousNotification;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsRecordMatch;

@NoArgsConstructor @Accessors(chain=true)
public class DnsDriverNotification extends SynchronousNotification {

    @Getter @Setter private BubbleDomain domain;
    public boolean hasDomain () { return domain != null; }

    @Getter @Setter private BubbleNetwork network;
    public boolean hasNetwork () { return network != null; }

    @Getter @Setter private BubbleNode node;
    public boolean hasNode () { return node != null; }

    @Getter @Setter private DnsRecord record;
    public boolean hasRecord () { return record != null; }

    @Getter @Setter private DnsRecordMatch matcher;

    @Getter @Setter private String dnsService;

    public DnsDriverNotification(BubbleNode node) { this.node = node; }

    public DnsDriverNotification(BubbleDomain domain) { this.domain = domain; }

    public DnsDriverNotification(BubbleNetwork network) { this.network = network; }

    public DnsDriverNotification(DnsRecord record) { this.record = record; }

    public DnsDriverNotification(DnsRecordMatch matcher) { this.matcher = matcher; }

}
