/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNetwork;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;

@Service @Slf4j
public class NodeLaunchMonitor extends SimpleDaemon {

    private static final long LAUNCH_ACTIVITY_TIMEOUT = SECONDS.toMillis(180);
    private static final long LAUNCH_TERMINATE_TIMEOUT = SECONDS.toMillis(5);

    @Getter private final long sleepTime = SECONDS.toMillis(15);

    @Autowired private BubbleConfiguration configuration;

    private final Map<String, LauncherEntry> launcherThreads = new ConcurrentHashMap<>();

    public void register(String networkUuid, Thread t) {
        startIfNotRunning();
        final LauncherEntry previousLaunch = launcherThreads.get(networkUuid);
        if (previousLaunch != null && previousLaunch.isAlive()) {
            log.warn("registerLauncher("+networkUuid+"): entry thread exists, stopping it: "+previousLaunch);
            forceEndLauncher(previousLaunch);
        }
        launcherThreads.put(networkUuid, new LauncherEntry(networkUuid, t));
    }

    public void cancel(String networkUuid) {
        final LauncherEntry previousLaunch = launcherThreads.get(networkUuid);
        if (previousLaunch == null || !previousLaunch.isAlive()) {
            log.warn("cancel("+networkUuid+"): entry does not thread exist, or is not alive: "+previousLaunch);
        } else {
            log.warn("cancel("+networkUuid+"): stopping active launch: "+previousLaunch);
            forceEndLauncher(previousLaunch);
        }
    }

    public boolean isRegistered(String networkUuid) {
        final LauncherEntry launcherEntry = launcherThreads.get(networkUuid);
        return launcherEntry != null && launcherEntry.isAlive();
    }

    private synchronized void startIfNotRunning() {
        if (!getIsAlive()) {
            final BubbleNetwork thisNetwork = configuration.getThisNetwork();
            if (thisNetwork == null) {
                die("register: thisNetwork is null");

            } else if (configuration.isSageLauncher() || thisNetwork.getInstallType() == AnsibleInstallType.sage) {
                log.info("register: first registration, starting launch monitor");
                start();

            } else {
                die("register: thisNetwork is not a sage");
            }
        }
    }

    public void touch(String networkUuid) {
        final LauncherEntry entry = launcherThreads.get(networkUuid);
        if (entry == null) {
            log.warn("touch: network not found: "+networkUuid+" from: "+ExceptionUtils.getStackTrace(new Exception()));
            return;
        }
        entry.touch();
        log.debug("touch: touched network: "+networkUuid);
    }

    public void unregister(String networkUuid) {
        log.info("unregister: removing network="+networkUuid);
        launcherThreads.remove(networkUuid);
    }

    @Override protected void process() {
        final Collection<LauncherEntry> values = new ArrayList<>(launcherThreads.values());
        for (LauncherEntry entry : values) {
            if (entry.age() > LAUNCH_ACTIVITY_TIMEOUT) {
                log.warn("process: entry is too old, stopping it: "+entry);
                forceEndLauncher(entry);
            }
        }
    }

    private void forceEndLauncher(LauncherEntry entry) {
        terminate(entry.getThread(), LAUNCH_TERMINATE_TIMEOUT);
        launcherThreads.remove(entry.getNetworkUuid());
    }

    @Override public void processException(Exception e) { log.warn("processException: "+shortError(e)); }

    @ToString
    private static class LauncherEntry {

        @Getter private final String networkUuid;
        @Getter private final Thread thread;
        @Getter private final long ctime;
        @Getter private volatile long mtime;

        public LauncherEntry(String networkUuid, Thread thread) {
            this.networkUuid = networkUuid;
            this.thread = thread;
            this.ctime = now();
            this.mtime = now();
        }

        public boolean isAlive() { return thread.isAlive(); }

        public void touch () { mtime = now(); }
        public long age () { return now() - mtime; }
    }

}
