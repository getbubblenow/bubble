/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.dns;

import bubble.cloud.dns.DnsServiceDriver;
import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.DelegatedNotificationHandlerBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.notify.NotificationType.dns_driver_response;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public abstract class NotificationHandler_dns_driver<T> extends DelegatedNotificationHandlerBase {

    @Autowired protected BubbleNodeDAO nodeDAO;
    @Autowired protected BubbleDomainDAO domainDAO;
    @Autowired protected BubbleNetworkDAO networkDAO;
    @Autowired protected CloudServiceDAO cloudDAO;

    @Override public void handleNotification(ReceivedNotification n) {

        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("sender not found: "+n.getFromNode());

        final DnsDriverNotification dnsNotification = json(n.getPayloadJson(), DnsDriverNotification.class);
        final BubbleDomain domain = getDomain(dnsNotification);
        final BubbleNetwork network = dnsNotification.hasNetwork() ? networkDAO.findByUuid(dnsNotification.getNetwork().getUuid()) : null;
        final CloudService dns = cloudDAO.findByUuid(dnsNotification.getDnsService());

        // sanity check for NPE and infinite loop
        if (dns == null) {
            die("handleNotification: DNS service not found: "+dnsNotification.getDnsService());

        } else if (dns.delegated()) {
            die("handleNotification: DNS service is delegated: "+dnsNotification.getDnsService());
        }

        try {
            final T result = handle(n, dnsNotification, domain, network, dns.getDnsDriver(configuration));
            notifySender(dns_driver_response, n.getNotificationId(), sender, result);
        } catch (Exception e) {
            log.error("handleNotification: "+e);
            notifySender(dns_driver_response, n.getNotificationId(), sender, e);
        }
    }

    public BubbleDomain getDomain(DnsDriverNotification dnsNotification) {
        if (dnsNotification.hasDomain()) return domainDAO.findByUuid(dnsNotification.getDomain().getUuid());
        if (dnsNotification.hasNetwork()) return domainDAO.findByUuid(dnsNotification.getNetwork().getDomain());
        if (dnsNotification.hasNode()) return domainDAO.findByUuid(dnsNotification.getNode().getDomain());
        if (dnsNotification.hasRecord()) return domainDAO.findByFqdn(dnsNotification.getRecord().getFqdn());
        log.warn("getDomain: no domain/network/node was set on notification, returning default domain");
        return domainDAO.findByUuid(configuration.getThisNode().getDomain());
    }

    protected abstract T handle(ReceivedNotification n,
                                DnsDriverNotification dnsNotification,
                                BubbleDomain domain,
                                BubbleNetwork network,
                                DnsServiceDriver dns) throws Exception;

}
