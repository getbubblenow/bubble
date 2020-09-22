/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.cloud;

import bubble.notify.NewNodeNotification;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static bubble.service.cloud.NodeProgressMeterConstants.*;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.closeQuietly;
import static org.cobbzilla.util.system.Bytes.KB;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

@Slf4j @Accessors(chain=true)
public class NodeProgressMeter extends PipedOutputStream implements Runnable {

    public static final int PIPE_SIZE = (int) (16*KB);

    public static final long TICK_REDIS_EXPIRATION = DAYS.toSeconds(1);
    public static final long MAX_TOUCH_INTERVAL = SECONDS.toMillis(10);

    private final List<NodeProgressMeterTick> standardTicks;

    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final List<NodeProgressMeterTick> ticks;
    @Getter private int tickPos = 0;
    private final AtomicBoolean error = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean success = new AtomicBoolean(false);
    public boolean success () { return success.get(); }

    private final Thread thread;

    private final RedisService redis;
    private final NewNodeNotification nn;
    private final String key;
    private final NodeProgressMeterTick lastStandardTick;

    private final StandardNetworkService networkService;
    private final NodeLaunchMonitor launchMonitor;

    private volatile long lastTouch = 0;

    public void touch() {
        if (now() > lastTouch + MAX_TOUCH_INTERVAL) {
            launchMonitor.touch(nn.getNetwork());
            networkService.confirmNetLock(nn.getNetwork(), nn.getLock());
            lastTouch = now();
        }
    }

    public NodeProgressMeter(NewNodeNotification nn,
                             RedisService redis,
                             StandardNetworkService networkService,
                             NodeLaunchMonitor launchMonitor) {

        this.nn = nn;
        this.redis = redis;
        this.networkService = networkService;
        this.launchMonitor = launchMonitor;

        standardTicks = getStandardTicks(nn);
        lastStandardTick = standardTicks.get(standardTicks.size()-1);

        ticks = new ArrayList<>(standardTicks);
        ticks.addAll(getInstallTicks(nn));

        key = nn.getUuid();

        final PipedInputStream pipeIn = new PipedInputStream(PIPE_SIZE);
        try {
            connect(pipeIn);
        } catch (IOException e) {
            die("NodeProgressMeter: error connecting pipe: "+shortError(e));
        }

        reader = new BufferedReader(new InputStreamReader(pipeIn));
        writer = new BufferedWriter(new OutputStreamWriter(this));

        thread = new Thread(this);
        thread.setName("NodeProgressMeter-"+nn.getFqdn());
        thread.setDaemon(true);
        thread.start();
    }

    public void write(String line) throws IOException {
        touch();
        if (closed.get()) {
            log.warn("write("+line+"): stream closed, not writing");
            return;
        }
        writer.write(line.endsWith("\n") ? line : line+"\n");
        writer.flush();
    }

    public boolean hasError () { return error.get(); }

    public void error(String line) {
        if (hasError()) {
            log.warn("error("+line+") ignored, error already set");
            return;
        }
        error.set(true);
        setCurrentTick(newTick(getErrorMessageKey(line), line));
    }

    private NodeProgressMeterTick newTick(String messageKey, String line) {
        return new NodeProgressMeterTick()
                .setAccount(nn.getAccount())
                .setNetwork(nn.getNetwork())
                .setMessageKey(messageKey)
                .setDetails(line);
    }

    public void resetToPreAnsible() {
        resetToTick(standardTicks.size());
        _setCurrentTick(lastStandardTick);
    }

    public void fullReset() {
        resetToTick(0);
    }

    public void resetToTick(int tickPos) {
        if (closed.get()) die("reset: progress meter is closed, cannot reset");
        error.set(false);
        this.tickPos = tickPos;
    }

    @Override public void run() {
        String line;
        try {
            while ((line = reader.readLine()) != null && !closed.get()) {
                touch();
                for (int i=tickPos; i<ticks.size(); i++) {
                    if (ticks.get(i).matches(line)) {
                        if (!error.get() && !closed.get()) setCurrentTick(ticks.get(i));
                        if (i > tickPos+1) {
                            if (tickPos+1 == i-1) {
                                log.warn("run: skipped one tick at index " + tickPos + ": " + json(ticks.get(tickPos), COMPACT_MAPPER));
                            } else {
                                log.warn("run: skipped " + (i - tickPos) + " ticks from index " + tickPos + " to " + (i - 1) + ": first skipped was " + json(ticks.get(tickPos), COMPACT_MAPPER) + " and last was " + json(ticks.get(i - 1), COMPACT_MAPPER));
                            }
                        }
                        tickPos = i+1;
                        break;
                    }
                }
                sleep(50, "checking for interrupt in between reads");
            }
        } catch (Exception e) {
            log.warn("run: "+e);
        }
    }

    public void setCurrentTick(NodeProgressMeterTick tick) { _setCurrentTick(tick, false); }
    public void _setCurrentTick(NodeProgressMeterTick tick) { _setCurrentTick(tick, true); }

    public void _setCurrentTick(NodeProgressMeterTick tick, boolean allowForce) {
        final String json = json(tick, COMPACT_MAPPER);
        if (!allowForce && closed.get()) {
            log.warn("_setCurrentTick (closed, not setting): "+json);
            return;
        }
        if (log.isTraceEnabled()) log.trace("_setCurrentTick: "+json+" from: "+stacktrace());
        redis.set(getProgressMeterKey(key, nn.getAccount()), json, EX, TICK_REDIS_EXPIRATION);
    }

    public static String getProgressMeterKey(String key, String accountUuid) { return accountUuid+":"+key; }
    public static String getProgressMeterPrefix(String accountUuid) { return accountUuid+":"; }

    public static final long THREAD_KILL_TIMEOUT = SECONDS.toMillis(5);

    @Override public void close() {
        if (log.isTraceEnabled()) log.trace("close: called from: "+stacktrace());
        closed.set(true);
        try {
            super.close();
        } catch (IOException e) {
            log.warn("close: "+e);
        }
        terminate(thread, THREAD_KILL_TIMEOUT);
        closeQuietly(reader);
        closeQuietly(writer);
    }

    public void completed() {
        if (log.isTraceEnabled()) log.trace("completed: called from: "+stacktrace());
        closed.set(true);
        success.set(true);
        touch();
        _setCurrentTick(new NodeProgressMeterTick()
                .setNetwork(nn.getNetwork())
                .setAccount(nn.getAccount())
                .setMessageKey(METER_COMPLETED_OK)
                .setPercent(100));
        background(this::close, "NodeProgressMeter.completed");
    }

    public NodeProgressMeter uncloseable() throws IOException {
        return new UncloseableNodeProgressMeter(this);
    }

    public void cancel () {
        if (log.isTraceEnabled()) log.trace("cancel: cancelling progress meter for network: "+nn.getNetworkName()+" from "+stacktrace());
        closed.set(true);
        success.set(true);
        _setCurrentTick(new NodeProgressMeterTick()
                .setNetwork(nn.getNetwork())
                .setAccount(nn.getAccount())
                .setMessageKey(METER_ERROR_CANCELED)
                .setPercent(0));
        background(this::close, "NodeProgressMeter.cancel");
    }

    private class UncloseableNodeProgressMeter extends NodeProgressMeter {
        private final NodeProgressMeter meter;
        public UncloseableNodeProgressMeter(NodeProgressMeter meter) throws IOException {
            super(meter.nn, meter.redis, meter.networkService, meter.launchMonitor);
            this.meter = meter;
        }
        @Override public void close() {}
        @Override public void cancel() { meter.cancel(); }
    }
}
