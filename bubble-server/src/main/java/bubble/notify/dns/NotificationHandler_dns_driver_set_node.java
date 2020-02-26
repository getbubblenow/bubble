/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify.dns;

import bubble.cloud.dns.DnsServiceDriver;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.notify.ReceivedNotification;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.dns.DnsRecord;

import java.util.Collection;

@Slf4j
public class NotificationHandler_dns_driver_set_node extends NotificationHandler_dns_driver<Collection<DnsRecord>> {

    @Override protected Collection<DnsRecord> handle(ReceivedNotification n,
                                                     DnsDriverNotification dnsNotification,
                                                     BubbleDomain domain,
                                                     BubbleNetwork network,
                                                     DnsServiceDriver dns) {
        return dns.setNode(dnsNotification.getNode());
    }

}
