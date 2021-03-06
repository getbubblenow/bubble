/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.notify.NewNodeNotification;
import bubble.server.BubbleConfiguration;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.system.Sleep.sleep;

@AllArgsConstructor @Slf4j
public class NodeLauncher implements Runnable {

    public static final int LAUNCH_MAX_RETRIES = 5;

    private final NewNodeNotification newNodeRequest;
    private final AtomicReference<String> lock;
    private final StandardNetworkService networkService;
    private final NodeLaunchMonitor launchMonitor;
    private final BubbleConfiguration configuration;

    @Override public void run() {
        final String networkUuid = newNodeRequest.getNetwork();
        final BubbleNetwork network = newNodeRequest.getNetworkObject();
        try {
            for (int i=0; i<LAUNCH_MAX_RETRIES; i++) {
                if (i > 0 && !launchMonitor.isRegistered(networkUuid)) {
                    log.warn("NodeLauncher.run: no longer registered: "+networkUuid);
                    return;
                }
                if (!lock.get().equals(newNodeRequest.getLock())) {
                    die("NodeLauncher.run: existingLock (" + lock.get() + ") is different than lock in NewNodeNotification: " + newNodeRequest.getLock());
                }
                if (!networkService.confirmNetLock(networkUuid, lock.get())) {
                    die("NodeLauncher.run: error confirming lock (" + lock.get() + ") for network: " + networkUuid);
                }

                final AtomicReference<BubbleNode> nodeRef = new AtomicReference<>();
                final AtomicReference<Exception> exceptionRef = new AtomicReference<>();
                final Thread launchThread = new Thread(new NodeLaunchThread(nodeRef, exceptionRef, networkService, newNodeRequest, launchMonitor));
                launchThread.setDaemon(true);
                launchThread.setName("NodeLaunchThread(network="+networkUuid+")");
                launchMonitor.register(newNodeRequest.getUuid(), networkUuid, launchThread);

                log.info("NodeLauncher.run: launching node..."+newNodeRequest.getFqdn());
                launchThread.start();
                do {
                    launchThread.join(SECONDS.toMillis(5));
                    if (log.isTraceEnabled()) log.trace("NodeLauncher.run: still waiting for thread join: "+newNodeRequest.getFqdn()+" stack="+stacktrace(launchThread));
                } while (launchThread.isAlive() && !launchThread.isInterrupted());

                if (launchThread.isInterrupted()) {
                    log.warn("NodeLauncher.run: launch interrupted while waiting for join, exiting early");
                    return;
                }

                final Exception exception = exceptionRef.get();
                final BubbleNode node = nodeRef.get();
                log.debug("NodeLauncher.run: node="+(node == null ? "null" : node.id())+", exception="+shortError(exception));
                if (exception != null) {
                    if (exception instanceof NodeLaunchException) {
                        final NodeLaunchException launchException = (NodeLaunchException) exception;
                        switch (launchException.getType()) {
                            case fatal:
                                die("NodeLauncher.run: fatal launch exception: " + shortError(launchException), launchException);
                                break;

                            case interrupted:
                                log.warn("NodeLauncher.run: launch interrupted, exiting early");
                                return;

                            case canRetry:
                                log.warn("NodeLauncher.run: nonfatal launch exception for node " + launchException.nodeSummary() + " : " + shortError(launchException));
                                break;

                            case unavailableRegion:
                                log.warn("NodeLauncher.run: unavailableRegion for node " + launchException.nodeSummary() + " : " + shortError(launchException));
                                if (newNodeRequest.getNetLocation().exactRegion()) {
                                    die("NodeLauncher.run: unavailableRegion and exactRegion set, cannot launch");
                                } else {
                                    if (launchException.getNode() != null) {
                                        log.warn("NodeLauncher.run: unavailableRegion and exactRegion not set, trying another region");
                                        newNodeRequest.excludeCurrentRegion(launchException.getNode());
                                    } else {
                                        die("NodeLauncher.run: unavailableRegion but node was null!");
                                    }
                                }
                                break;

                            default:
                                die("NodeLauncher.run: unknown launch exception (type="+launchException.getType()+"): "+shortError(launchException), launchException);
                        }
                    } else {
                        if (!configuration.testMode()) die("NodeLauncher.run: fatal launch exception: " + shortError(exception), exception);
                    }
                }
                if (node != null && node.isRunning()) {
                    log.info("NodeLauncher.run: successfully launched node: "+node.id());
                    return;
                }

                log.warn("NodeLauncher.run: error starting node: state: " + (node == null ? "null node" : node.getState()));
                sleep((i+1)*SECONDS.toMillis(2), "waiting to relaunch node that failed to launch");
                newNodeRequest.setNodeHost(network);  // assign a new hostname if necessary
            }

        } catch (Exception e) {
            log.error("NodeLauncher.run: error: "+e, e);

        } finally {
            if (lock.get() != null) networkService.unlockNetwork(networkUuid, lock.get());
            launchMonitor.unregister(networkUuid);
        }
    }

    @AllArgsConstructor
    private class NodeLaunchThread implements Runnable {

        private final AtomicReference<BubbleNode> nodeRef;
        private final AtomicReference<Exception> exceptionRef;
        private final StandardNetworkService networkService;
        private final NewNodeNotification newNodeRequest;
        private final NodeLaunchMonitor launchMonitor;

        @Override public void run() {
            try {
                nodeRef.set(networkService.newNode(newNodeRequest.setLock(lock.get()), launchMonitor));
            } catch (Exception e) {
                log.warn("run: exception: "+shortError(e));
                exceptionRef.set(e);
            }
        }
    }

}
