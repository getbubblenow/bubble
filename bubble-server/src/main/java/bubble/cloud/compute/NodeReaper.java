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
import bubble.service.cloud.NetworkService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.cobbzilla.util.network.NetworkUtil;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.util.time.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Slf4j
public class NodeReaper extends SimpleDaemon {

    private static final long STARTUP_DELAY = MINUTES.toMillis(30);
    private static final long KILL_CHECK_INTERVAL = MINUTES.toMillis(30);
    private static final long MAX_DOWNTIME_BEFORE_DELETION = DAYS.toMillis(2);

    private final ComputeServiceDriverBase compute;

    public NodeReaper(ComputeServiceDriverBase compute) { this.compute = compute; }

    @Override protected long getStartupDelay() { return STARTUP_DELAY; }
    @Override protected long getSleepTime() { return KILL_CHECK_INTERVAL; }

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleDomainDAO domainDAO;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;
    @Autowired private NetworkService networkService;

    private final Map<String, Long> unreachableSince = new HashMap<>(100);

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
        if (wouldKillSelf(node)) return;
        final var found = nodeDAO.findByIp4(node.getIp4());
        if (found == null) {
            final String message = prefix() + "processNode: no node exists with ip4=" + node.getIp4() + ", killing it";
            log.warn(message);
            reportError(message);
            final var domain = domainDAO.findByUuid(node.getDomain());
            final var dns = domain != null ? cloudDAO.findByUuid(domain.getPublicDns()) : null;
            try {
                if (dns != null) dns.getDnsDriver(configuration).deleteNode(node);
                compute.stop(node);
            } catch (Exception e) {
                final String errMessage = prefix() + "processNode: error stopping node " + node.getIp4();
                reportError(errMessage, e);
                log.error(errMessage, e);
            }
        } else {
            if (networkService.isReachable(node)) {
                unreachableSince.remove(node.getUuid());
            } else {
                final long downTime = unreachableSince.computeIfAbsent(node.getUuid(), k -> now());
                if (now() - downTime > MAX_DOWNTIME_BEFORE_DELETION) {
                    final String message = prefix() + "processNode: deleting node (" + node.id() + ") that has been down since " + TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH_mm_ss.print(downTime);
                    log.warn(message);
                    reportError(message);
                    nodeDAO.delete(node.getUuid());
                }
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
