/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.dns;

import bubble.cloud.dns.DnsServiceDriver;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.notify.ReceivedNotification;
import org.cobbzilla.util.dns.DnsRecord;

import java.util.Collection;

public class NotificationHandler_dns_driver_list extends NotificationHandler_dns_driver<Collection<DnsRecord>> {

    @Override protected Collection<DnsRecord> handle(ReceivedNotification n,
                                                     DnsDriverNotification dnsNotification,
                                                     BubbleDomain domain,
                                                     BubbleNetwork network,
                                                     DnsServiceDriver dns) {
        return dns.list(dnsNotification.getMatcher());
    }

}
