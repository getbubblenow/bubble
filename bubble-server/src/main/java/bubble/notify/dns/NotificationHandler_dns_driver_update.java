package bubble.notify.dns;

import bubble.cloud.dns.DnsServiceDriver;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.notify.ReceivedNotification;
import org.cobbzilla.util.dns.DnsRecord;

public class NotificationHandler_dns_driver_update extends NotificationHandler_dns_driver<DnsRecord> {

    @Override protected DnsRecord handle(ReceivedNotification n,
                                         DnsDriverNotification dnsNotification,
                                         BubbleDomain domain,
                                         BubbleNetwork network,
                                         DnsServiceDriver dns) {
        return dns.update(dnsNotification.getRecord());
    }

}
