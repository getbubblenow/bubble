/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify.dns;

import bubble.cloud.dns.DnsServiceDriver;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.notify.ReceivedNotification;
import org.cobbzilla.util.dns.DnsRecord;

public class NotificationHandler_dns_driver_remove extends NotificationHandler_dns_driver<DnsRecord> {

    @Override protected DnsRecord handle(ReceivedNotification n,
                                         DnsDriverNotification dnsNotification,
                                         BubbleDomain domain,
                                         BubbleNetwork network,
                                         DnsServiceDriver dns) {
        return dns.remove(dnsNotification.getRecord());
    }

}
