/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import bubble.dao.cloud.BubbleDomainDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.cobbzilla.util.network.NetworkUtil;
import org.cobbzilla.util.string.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class NodeReaper extends SimpleDaemon {

    private static final long STARTUP_DELAY = MINUTES.toMillis(30);
    private static final long KILL_CHECK_INTERVAL = MINUTES.toMillis(30);

    private final ComputeServiceDriverBase compute;

    public NodeReaper(ComputeServiceDriverBase compute) { this.compute = compute; }

    @Override protected long getStartupDelay() { return STARTUP_DELAY; }
    @Override protected long getSleepTime() { return KILL_CHECK_INTERVAL; }

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    private String prefix() { return compute.getClass().getSimpleName()+": "; }

    @Override protected void process() {
        try {
            compute.listNodes().forEach(this::processNode);
        } catch (Exception e) {
            log.warn(prefix()+"process: "+e);
            sleep(getSleepTime(), "waiting to process after exception: "+e);
        }
    }

    private void processNode(@NonNull final BubbleNode node) {
        final var found = nodeDAO.findByIp4(node.getIp4());
        if (found == null && !wouldKillSelf(node)) {
            log.warn(prefix() + "processNode: no node exists with ip4=" + node.getIp4() + ", killing it");
            final var domain = domainDAO.findByUuid(node.getDomain());
            final var dns = domain != null ? cloudDAO.findByUuid(domain.getPublicDns()) : null;
            try {
                if (dns != null) dns.getDnsDriver(configuration).deleteNode(node);
                compute.stop(node);
            } catch (Exception e) {
                log.error(prefix() + "processNode: error stopping node " + node.getIp4(), e);
            }
        }
    }

    private boolean wouldKillSelf(@NonNull final BubbleNode node) {
        if (node.hasSameIp(configuration.getThisNode())) {
            log.debug(prefix() + "wouldKillSelf: not killing configuration.thisNode: "
                      + node.getIp4() + "/" + node.getIp6());
            return true;
        }

        final var localIps = NetworkUtil.configuredIps();
        if (localIps.contains(node.getIp4()) || localIps.contains(node.getIp6())) {
            log.debug(prefix() + "wouldKillSelf: not killing self, IP matches one of: "
                      + StringUtil.toString(localIps));
            return true;
        }

        return false;
    }

}
