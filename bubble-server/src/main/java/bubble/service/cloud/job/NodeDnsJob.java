/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud.job;

import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleDomain;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.server.BubbleConfiguration;

import static bubble.service.cloud.NodeProgressMeterConstants.METER_ERROR_DNS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

public class NodeDnsJob implements Runnable {

    private static final long DNS_TIMEOUT = MINUTES.toMillis(60);

    private final CloudServiceDAO cloudDAO;
    private final BubbleDomain domain;
    private final BubbleNetwork network;
    private final BubbleNode node;
    private final BubbleConfiguration configuration;

    public NodeDnsJob(CloudServiceDAO cloudDAO,
                      BubbleDomain domain,
                      BubbleNetwork network,
                      BubbleNode node,
                      BubbleConfiguration configuration) {
        this.cloudDAO = cloudDAO;
        this.domain = domain;
        this.network = network;
        this.node = node;
        this.configuration = configuration;
    }

    @Override public void run() {
        try {
            node.waitForIpAddresses();
            final CloudService dnsService = cloudDAO.findByUuid(domain.getPublicDns());
            dnsService.getDnsDriver(configuration).setNode(node);

            // ensure this hostname is visible in our DNS and in public DNS,
            // or else node can't create its own letsencrypt SSL cert
            // ensure it resolves authoritatively first, if anyone else asks about it, they might
            // cache the fact that it doesn't exist for a long time

            // sometimes this takes a very long time. disable this for now, we have added retries to init_certbot.sh
//            final DnsServiceDriver dnsDriver = dnsService.getDnsDriver(configuration);
//            dnsDriver.ensureResolvable(domain, node, DNS_TIMEOUT);
        } catch (Exception e) {
            throw new NodeJobException(METER_ERROR_DNS, "run: error setting up DNS for node: "+shortError(e), e);
        }
    }
}
