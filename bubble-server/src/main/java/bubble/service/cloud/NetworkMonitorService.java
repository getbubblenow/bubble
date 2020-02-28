/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNetworkState;
import bubble.service.boot.StandardSelfNodeService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static bubble.ApiConstants.ROOT_NETWORK_UUID;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.time.TimeUtil.formatDuration;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Service @Slf4j
public class NetworkMonitorService extends SimpleDaemon {

    private static final long STARTUP_DELAY = MINUTES.toMillis(1);
    private static final long CHECK_INTERVAL = MINUTES.toMillis(30);
    private static final long NO_NODES_GRACE_PERIOD = HOURS.toMillis(1);

    @Override protected long getStartupDelay() { return STARTUP_DELAY; }
    @Override protected long getSleepTime() { return CHECK_INTERVAL; }

    @Autowired private BubbleNetworkDAO networkDAO;
    @Autowired private StandardNetworkService networkService;
    @Autowired private StandardSelfNodeService selfNodeService;

    @Override protected void process() {
        final BubbleNetwork thisNetwork = selfNodeService.getThisNetwork();
        try {
            for (BubbleNetwork network : networkDAO.findAll()) {

                // never update the root network
                if (network.getUuid().equals(ROOT_NETWORK_UUID)) continue;

                // if we are looking at ourselves, obviously we are running
                if (thisNetwork != null && network.getUuid().equals(thisNetwork.getUuid())) {
                    if (network.getState() != BubbleNetworkState.running) {
                        networkDAO.update(network.setState(BubbleNetworkState.running));
                        selfNodeService.refreshThisNetwork();
                    }
                    continue;
                }

                if (networkService.anyNodesActive(network)) {
                    switch (network.getState()) {
                        case starting: case running: case restoring: continue;
                        default:
                            reportError(getName()+": network "+network.getNetworkDomain()+" has nodes running but state is "+network.getState());
                    }
                } else {
                    if (network.getState() != BubbleNetworkState.stopped && network.getState() != BubbleNetworkState.error_stopping) {
                        if (network.getCtimeAge() < NO_NODES_GRACE_PERIOD) {
                            log.warn(getName() + ": network " + network.getNetworkDomain() + " does NOT have nodes running but state is " + network.getState() + ", we would normally mark it 'error_stopping' but it is less than "+formatDuration(NO_NODES_GRACE_PERIOD)+" old");
                        } else {
                            reportError(getName() + ": network " + network.getNetworkDomain() + " does NOT have nodes running but state is " + network.getState() + ", marking it 'error_stopping'");
                            networkDAO.update(network.setState(BubbleNetworkState.error_stopping));
                        }
                    }
                }
            }

        } catch (Exception e) {
            reportError(getName()+": fatal error: "+shortError(e), e);
        }
    }
}
