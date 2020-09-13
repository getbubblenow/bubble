/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.device;

import bubble.cloud.geoLocation.GeoLocation;
import bubble.dao.device.FlexRouterDAO;
import bubble.model.device.DeviceStatus;
import bubble.model.device.FlexRouter;
import bubble.model.device.FlexRouterPing;
import bubble.service.cloud.GeoService;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.cobbzilla.util.collection.SingletonSet;
import org.cobbzilla.util.daemon.AwaitResult;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.io.FileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.model.device.FlexRouterPing.MAX_PING_AGE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.Await.awaitAll;
import static org.cobbzilla.util.daemon.DaemonThreadFactory.fixedPool;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpMethods.POST;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.EMPTY_ARRAY;
import static org.cobbzilla.util.system.CommandShell.chmod;
import static org.cobbzilla.util.system.Sleep.sleep;

@Service @Slf4j
public class StandardFlexRouterService extends SimpleDaemon implements FlexRouterService {

    public static final int MAX_PING_TRIES = 5;

    private static final long PING_SLEEP_FACTOR = SECONDS.toMillis(2);

    // HttpClient timeouts are in seconds
    public static final int DEFAULT_PING_TIMEOUT = (int) SECONDS.toSeconds(MAX_PING_AGE/2);
    public static final RequestConfig DEFAULT_PING_REQUEST_CONFIG = RequestConfig.custom()
            .setConnectTimeout(DEFAULT_PING_TIMEOUT)
            .setSocketTimeout(DEFAULT_PING_TIMEOUT)
            .setConnectionRequestTimeout(DEFAULT_PING_TIMEOUT).build();

    // wait for ssh key to be written
    private static final long FIRST_TIME_WAIT = SECONDS.toMillis(10);
    private static final long INTERRUPT_WAIT = FIRST_TIME_WAIT/2;

    public static final long PING_ALL_TIMEOUT
            = (SECONDS.toMillis(1) * DEFAULT_PING_TIMEOUT * MAX_PING_TRIES) + FIRST_TIME_WAIT;

    // thread pool size
    public static final int DEFAULT_MAX_TUNNELS = 5;

    private static CloseableHttpClient getHttpClient() {
        return HttpClientBuilder.create()
                .setDefaultRequestConfig(DEFAULT_PING_REQUEST_CONFIG)
                .build();
    }

    public static final long DEFAULT_SLEEP_TIME = MINUTES.toMillis(2);

    @Autowired private FlexRouterDAO flexRouterDAO;
    @Autowired private GeoService geoService;
    @Autowired private DeviceService deviceService;

    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    @Override public void onStart() {
        flexRouterDAO.findEnabledAndRegistered().forEach(this::register);
        super.onStart();
    }

    @Override protected boolean canInterruptSleep() { return true; }

    @Override protected long getSleepTime() { return interrupted.get() ? 0 : DEFAULT_SLEEP_TIME; }

    @Override public void interruptSoon() {
        if (log.isTraceEnabled()) log.trace("interruptSoon: will interrupt in "+INTERRUPT_WAIT+" millis");
        synchronized (interrupted) {
            if (interrupted.get()) {
                if (log.isTraceEnabled()) log.trace("interruptSoon: interrupt flag already set, not setting again");
            }
            interrupted.set(true);
        }
        background(() -> {
            sleep(INTERRUPT_WAIT);
            if (log.isTraceEnabled()) log.trace("interruptSoon: interrupting...");
            interrupt();
        }, "StandardFlexRouterService.interruptSoon");
    }

    public void register (FlexRouter router) { allowFlexKey(router.getKey()); }

    public void unregister (FlexRouter router) {
        // todo: kill tunnel process if running
        disallowFlexKey(router.getKey());
    }

    private final Map<String, FlexRouterStatus> statusMap = new ConcurrentHashMap<>(DEFAULT_MAX_TUNNELS);
    private final Map<String, FlexRouterInfo> activeRouters = new ConcurrentHashMap<>(DEFAULT_MAX_TUNNELS);

    public FlexRouterStatus status(String uuid) {
        final FlexRouterStatus stat = statusMap.get(uuid);
        if (stat == FlexRouterStatus.unreachable) interruptSoon();
        return stat == null ? FlexRouterStatus.none : stat;
    }

    public FlexRouterStatus setStatus(FlexRouter router, FlexRouterStatus status) {
        statusMap.put(router.getUuid(), status);
        if (status == FlexRouterStatus.active && router.initialized()) {
            final FlexRouterInfo info = activeRouters.get(router.getUuid());
            if (info == null || info.hasNoDeviceStatus()) {
                try {
                    final DeviceStatus deviceStatus = deviceService.getDeviceStatus(router.getDevice());
                    activeRouters.put(router.getUuid(), new FlexRouterInfo(router, deviceStatus));
                } catch (Exception e) {
                    log.error("setStatus: error creating FlexRouterInfo: "+shortError(e));
                }
            }
        } else {
            activeRouters.remove(router.getUuid());
        }
        return status;
    }

    public Set<FlexRouterInfo> selectClosestRouter (String accountUuid, String publicIp, String vpnIp) {
        final GeoLocation geoLocation = publicIp == null ? null : geoService.locate(accountUuid, publicIp);
        final Collection<FlexRouterInfo> values = activeRouters.values();
        switch (values.size()) {
            case 0: return Collections.emptySet();
            case 1: return new SingletonSet<>(values.iterator().next());
            default:
                final Set<FlexRouterInfo> candidates = new TreeSet<>(new FlexRouterProximityComparator(geoLocation, vpnIp));
                candidates.addAll(values);
                return candidates;
        }
    }

    @Override protected void process() {
        synchronized (interrupted) { interrupted.set(false); }
        try {
            @Cleanup final CloseableHttpClient httpClient = getHttpClient();
            final List<FlexRouter> routers = flexRouterDAO.findEnabledAndRegistered();
            if (log.isTraceEnabled()) log.trace("process: starting, will ping "+routers.size()+" routers");
            final List<Future<?>> futures = new ArrayList<>();
            @Cleanup("shutdownNow") final ExecutorService exec = fixedPool(DEFAULT_MAX_TUNNELS, "StandardFlexRouterService.process");
            for (FlexRouter router : routers) {
                futures.add(exec.submit(() -> {
                    final long firstTimeDelay = now() - router.getCtime();
                    if (firstTimeDelay < FIRST_TIME_WAIT) {
                        sleep(FIRST_TIME_WAIT - firstTimeDelay, "process: waiting for flex ssh key");
                    }
                    boolean active = pingFlexRouter(router, httpClient);
                    if (active != router.active() || (active && router.uninitialized())) {
                        if (active && router.uninitialized()) {
                            router.setInitialized(true);
                        }
                        router.setActive(active);
                        flexRouterDAO.update(router);
                    }
                    return active;
                }));
            }
            final AwaitResult<Boolean> awaitResult = awaitAll(futures, PING_ALL_TIMEOUT);
            if (log.isTraceEnabled()) log.trace("process: awaitResult="+awaitResult);

        } catch (Exception e) {
            log.error("process: "+shortError(e));
        }
    }

    public boolean pingFlexRouter(FlexRouter router, HttpClient httpClient) {
        allowFlexKey(router.getKey());
        final String pingUrl = router.proxyBaseUri() + "/ping";
        final HttpRequestBean request = new HttpRequestBean(POST, pingUrl);
        final String prefix = "pingRouter(" + router + "): ";
        for (int i=0; i<MAX_PING_TRIES; i++) {
            sleep(PING_SLEEP_FACTOR * i, "waiting to ping flexRouter");
            if (i == 0) {
                if (log.isTraceEnabled()) log.trace(prefix+"pinging router at "+pingUrl+" ...");
            } else {
                final FlexRouter existing = flexRouterDAO.findByUuid(router.getUuid());
                if (existing == null) {
                    log.error(prefix+"router no longer exists: "+router.getUuid());
                    setStatus(router, FlexRouterStatus.deleted);
                    return false;
                } else {
                    router = existing;
                }
                if (log.isInfoEnabled()) log.info(prefix+"attempting to ping again (try="+(i+1)+"/"+MAX_PING_TRIES+")");
            }
            try {
                request.setEntity(json(router.pingObject()));
                final HttpResponseBean response = HttpUtil.getResponse(request, httpClient);
                if (!response.isOk()) {
                    log.error(prefix+"response not OK: "+response);
                } else {
                    final FlexRouterPing pong = response.getEntity(FlexRouterPing.class);
                    if (pong.validate(router)) {
                        // emit message if loglevel is tracing, or info message if router was previously inactive
                        if (log.isTraceEnabled() || (router.inactive() && log.isInfoEnabled())) log.trace(prefix+"router is ok");
                        setStatus(router, FlexRouterStatus.active);
                        return true;
                    } else {
                        log.error(prefix+"pong response was invalid");
                    }
                }

            } catch (Exception e) {
                log.info(prefix+"error: "+shortError(e));
            }
            setStatus(router, FlexRouterStatus.unreachable);
        }
        log.error(prefix+"error: router failed after "+MAX_PING_TRIES+" attempts, returning false");
        return false;
    }

    public synchronized void allowFlexKey(String key) {
        final String trimmedKey = key.trim();
        final File authFile = getAuthFile();
        final String authFileContents = FileUtil.toStringOrDie(authFile);
        final String[] lines = authFileContents == null ? EMPTY_ARRAY : authFileContents.split("\n");
        if (Arrays.stream(lines).anyMatch(line -> line.trim().equals(trimmedKey))) {
            if (log.isDebugEnabled()) log.debug("allowKey: already present: "+trimmedKey);
        } else {
            final File temp = temp("flex_keys_", ".tmp");
            final String dataToWrite = authFileContents != null && authFileContents.endsWith("\n") ? trimmedKey + "\n" : "\n" + trimmedKey + "\n";
            toFileOrDie(temp, authFileContents, true);
            toFileOrDie(temp, dataToWrite, true);
            renameOrDie(temp, authFile);
            if (log.isInfoEnabled()) log.info("allowKey: added key: "+trimmedKey);
        }
    }

    public synchronized void disallowFlexKey(String key) {
        final String trimmedKey = key.trim();
        final File authFile = getAuthFile();
        final String authFileContents = FileUtil.toStringOrDie(authFile);
        final String[] lines = authFileContents == null ? EMPTY_ARRAY : authFileContents.split("\n");
        final StringBuilder b = new StringBuilder();
        boolean found = false;
        for (String line : lines) {
            if (b.length() > 0) b.append("\n");
            if (line.trim().equals(trimmedKey)) {
                found = true;
            } else {
                b.append(line);
            }
        }
        b.append("\n");
        final File temp = temp("flex_keys_", ".tmp");
        toFileOrDie(temp, b.toString());
        renameOrDie(temp, authFile);
        if (found) {
            if (log.isInfoEnabled()) log.info("disallowKey: removed key from authorized_keys file: "+trimmedKey);
        } else {
            if (log.isInfoEnabled()) log.info("disallowKey: key was not found in authorized_keys file: "+trimmedKey);
        }
    }

    private static File getAuthFile() {
        final File sshDir = new File(HOME_DIR+"/.ssh");
        if (!sshDir.exists()) {
            mkdirOrDie(sshDir);
        }
        chmod(sshDir, "700");
        final File authFile = new File(sshDir, "flex_authorized_keys");
        if (!authFile.exists()) {
            touch(authFile);
        }
        chmod(authFile, "600");
        return authFile;
    }

}
