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
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.cobbzilla.util.network.NetworkUtil;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.util.time.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
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
    private static final long MAX_TIME_NOT_IN_DB_BEFORE_DELETION = MINUTES.toMillis(45);
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

    private final Map<String, Long> noNodeInDb = new ExpirationMap<>(100, MAX_TIME_NOT_IN_DB_BEFORE_DELETION*2);
    private final Map<String, Long> unreachableSince = new ExpirationMap<>(100, MAX_DOWNTIME_BEFORE_DELETION*2);

    private String prefix() { return compute.getClass().getSimpleName()+": "; }

    @Override protected void process() {
        try {
            log.info(prefix()+"process: starting for cloud: "+compute.getClass().getSimpleName());
            final List<BubbleNode> nodes = compute.listNodes();
            log.info(prefix()+"process: processing "+nodes.size()+" nodes for cloud: "+compute.getClass().getSimpleName());
            nodes.forEach(this::processNode);
        } catch (Exception e) {
            log.warn(prefix()+"process: "+e);
            sleep(getSleepTime(), "waiting to process after exception: "+e);
        }
    }

    private void processNode(@NonNull final BubbleNode node) {
        if (wouldKillSelf(node)) return;
        final var nodeFromDB = nodeDAO.findByIp4(node.getIp4());
        if (nodeFromDB == null) {
            final Long notInDbSince = noNodeInDb.get(node.getIp4());
            if (notInDbSince == null) {
                noNodeInDb.put(node.getIp4(), now());

            } else if (now() - notInDbSince > MAX_TIME_NOT_IN_DB_BEFORE_DELETION) {
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
            }
        } else {
            noNodeInDb.remove(nodeFromDB.getIp4());
            if (networkService.isReachable(nodeFromDB)) {
                unreachableSince.remove(nodeFromDB.getUuid());
            } else {
                final var downTime = unreachableSince.get(nodeFromDB.getUuid());
                if (downTime == null) {
                    unreachableSince.put(nodeFromDB.getUuid(), now());
                } else if (now() - downTime > MAX_DOWNTIME_BEFORE_DELETION) {
                    final var message = prefix() + "processNode: deleting node that has been down since "
                                        + TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH_mm_ss.print(downTime)
                                        + " node=" + nodeFromDB.id();
                    log.warn(message);
                    reportError(message);
                    nodeDAO.delete(nodeFromDB.getUuid());
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
