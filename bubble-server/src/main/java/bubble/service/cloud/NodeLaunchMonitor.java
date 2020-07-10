/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.model.cloud.AnsibleInstallType;
import bubble.model.cloud.BubbleNetwork;
import bubble.notify.NewNodeNotification;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.cobbzilla.util.collection.ExpirationEvictionPolicy;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.service.cloud.NodeProgressMeter.getProgressMeterKey;
import static bubble.service.cloud.NodeProgressMeter.getProgressMeterPrefix;
import static java.util.concurrent.TimeUnit.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.json;

@Service @Slf4j
public class NodeLaunchMonitor extends SimpleDaemon {

    private static final long LAUNCH_ACTIVITY_TIMEOUT = SECONDS.toMillis(180);
    private static final long LAUNCH_TERMINATE_TIMEOUT = MINUTES.toMillis(6);

    @Getter private final long sleepTime = SECONDS.toMillis(15);

    @Override public void processException(Exception e) { log.warn("processException: "+shortError(e)); }

    @Autowired private BubbleConfiguration configuration;
    @Autowired private RedisService redis;
    @Autowired private StandardNetworkService networkService;

    @Getter(lazy=true) private final RedisService networkSetupStatus = redis.prefixNamespace(getClass().getSimpleName()+"_status_");

    private final Map<String, LauncherEntry> launcherThreads = new ConcurrentHashMap<>();

    public void register(String nnUuid, String networkUuid, Thread t) {
        startIfNotRunning();
        final LauncherEntry previousLaunch = launcherThreads.get(networkUuid);
        if (previousLaunch != null && previousLaunch.isAlive()) {
            log.warn("registerLauncher("+networkUuid+"): entry thread exists, stopping it: "+previousLaunch);
            forceEndLauncher(previousLaunch);
        }
        launcherThreads.put(networkUuid, new LauncherEntry(nnUuid, networkUuid, t));
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
        final NodeProgressMeter meter = getProgressMeter(entry.getNnUuid());
        if (meter != null) meter.cancel();
        terminate(entry.getThread(), LAUNCH_TERMINATE_TIMEOUT);
        launcherThreads.remove(entry.getNetworkUuid());
    }

    private final Map<String, NodeProgressMeter> progressMeters = new ExpirationMap<>(50, HOURS.toMillis(1), ExpirationEvictionPolicy.atime);

    public NodeProgressMeter getProgressMeter(String nnId) { return progressMeters.get(nnId); }

    public NodeProgressMeter getProgressMeter(@NonNull NewNodeNotification nn) {
        return progressMeters.computeIfAbsent(nn.getUuid(), k -> new NodeProgressMeter(nn, getNetworkSetupStatus(), networkService, this));
    }

    public NodeProgressMeterTick getLaunchStatus(String accountUuid, String uuid) {
        final String json = getNetworkSetupStatus().get(getProgressMeterKey(uuid, accountUuid));
        if (json == null) return null;
        try {
            final NodeProgressMeterTick tick = json(json, NodeProgressMeterTick.class);
            if (!tick.hasAccount() || !tick.getAccount().equals(accountUuid)) {
                log.warn("getLaunchStatus: tick.account != accountUuid, returning null");
                return null;
            }
            return tick.setPattern(null);
        } catch (Exception e) {
            return die("getLaunchStatus: "+e);
        }
    }

    public List<NodeProgressMeterTick> listLaunchStatuses(String accountUuid) {
        return listLaunchStatuses(accountUuid, null);
    }

    public List<NodeProgressMeterTick> listLaunchStatuses(String accountUuid, String networkUuid) {
        final RedisService stats = getNetworkSetupStatus();
        final List<NodeProgressMeterTick> ticks = new ArrayList<>();
        for (String key : stats.keys(getProgressMeterPrefix(accountUuid)+"*")) {
            final String json = stats.get_withPrefix(key);
            if (json != null) {
                try {
                    final NodeProgressMeterTick tick = json(json, NodeProgressMeterTick.class).setPattern(null);
                    if (networkUuid != null && tick.hasNetwork() && networkUuid.equals(tick.getNetwork())) {
                        ticks.add(tick);
                    }
                } catch (Exception e) {
                    log.warn("currentTicks (bad json?): "+e);
                }
            }
        }
        return ticks;
    }

    @ToString
    private static class LauncherEntry {

        @Getter private final String nnUuid;
        @Getter private final String networkUuid;
        @Getter private final Thread thread;
        @Getter private final long ctime;
        @Getter private volatile long mtime;

        public LauncherEntry(String nnUuid, String networkUuid, Thread thread) {
            this.nnUuid = nnUuid;
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
