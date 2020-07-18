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
import lombok.extern.slf4j.Slf4j;

import static bubble.service.cloud.NodeProgressMeterConstants.METER_ERROR_DNS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class NodeDnsJob implements Runnable {

    private static final long DNS_TIMEOUT = MINUTES.toMillis(60);
    private static final int MAX_DNS_ATTEMPTS = 10;

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
            Exception lastEx = null;
            for (int i=0; i<MAX_DNS_ATTEMPTS; i++) {
                try {
                    dnsService.getDnsDriver(configuration).setNode(node);
                    lastEx = null;
                    break;
                } catch (Exception e) {
                    lastEx = e;
                    log.error("run(attempt "+i+"): "+shortError(e));
                    sleep(SECONDS.toMillis(3)*i, "waiting to retry DnsDriver.setNode("+node.id()+")");
                }
            }
            if (lastEx != null) throw lastEx;

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
