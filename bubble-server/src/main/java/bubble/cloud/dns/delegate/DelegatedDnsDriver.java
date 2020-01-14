package bubble.cloud.dns.delegate;

import bubble.cloud.DelegatedCloudServiceDriverBase;
import bubble.cloud.dns.DnsServiceDriver;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.notify.dns.DnsDriverNotification;
import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.dns.DnsRecordMatch;

import java.util.Arrays;
import java.util.Collection;

import static bubble.model.cloud.notify.NotificationType.*;

public class DelegatedDnsDriver extends DelegatedCloudServiceDriverBase implements DnsServiceDriver {

    public DelegatedDnsDriver(CloudService cloud) { super(cloud); }

    @Override public Collection<DnsRecord> create(BubbleDomain domain) {
        final BubbleNode delegate = getDelegateNode();
        final DnsRecord[] records = notificationService.notifySync(delegate, dns_driver_create, notification(new DnsDriverNotification(domain)));
        return Arrays.asList(records);
    }

    @Override public Collection<DnsRecord> setNetwork(BubbleNetwork network) {
        final BubbleNode delegate = getDelegateNode();
        final DnsRecord[] records = notificationService.notifySync(delegate, dns_driver_set_network, notification(new DnsDriverNotification(network)));
        return Arrays.asList(records);
    }

    @Override public Collection<DnsRecord> setNode(BubbleNode node) {
        final BubbleNode delegate = getDelegateNode();
        final DnsRecord[] records = notificationService.notifySync(delegate, dns_driver_set_node, notification(new DnsDriverNotification(node)));
        return Arrays.asList(records);
    }

    @Override public Collection<DnsRecord> deleteNode(BubbleNode node) {
        final BubbleNode delegate = getDelegateNode();
        final DnsRecord[] records = notificationService.notifySync(delegate, dns_driver_delete_node, notification(new DnsDriverNotification(node)));
        return Arrays.asList(records);
    }

    @Override public DnsRecord update(DnsRecord record) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, dns_driver_update, notification(record));
    }

    @Override public DnsRecord remove(DnsRecord record) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, dns_driver_remove, notification(record));
    }

    @Override public Collection<DnsRecord> list(DnsRecordMatch matcher) {
        final BubbleNode delegate = getDelegateNode();
        final DnsRecord[] records = notificationService.notifySync(delegate, dns_driver_list, notification(new DnsDriverNotification(matcher)));
        return Arrays.asList(records);
    }

    private DnsDriverNotification notification(DnsDriverNotification n) { return n.setDnsService(cloud.getDelegated()); }

    private DnsDriverNotification notification(DnsRecord record) {
        return notification(new DnsDriverNotification(record));
    }

}
