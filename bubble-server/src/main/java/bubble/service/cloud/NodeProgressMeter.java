package bubble.service.cloud;

import bubble.notify.NewNodeNotification;
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

    private final List<NodeProgressMeterTick> standardTicks;

    private BufferedReader reader;
    private BufferedWriter writer;
    private List<NodeProgressMeterTick> ticks;
    private int tickPos = 0;
    private AtomicBoolean error = new AtomicBoolean(false);
    private AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread thread;

    private RedisService redis;
    private NewNodeNotification nn;
    private String key;
    private final NodeProgressMeterTick lastStandardTick;

    public NodeProgressMeter(NewNodeNotification nn, RedisService redis) throws IOException {

        this.nn = nn;
        this.redis = redis;

        standardTicks = getStandardTicks(nn);
        lastStandardTick = standardTicks.get(standardTicks.size()-1);

        ticks = new ArrayList<>(standardTicks);
        ticks.addAll(getInstallTicks(nn));

        key = nn.getUuid();

        final PipedInputStream pipeIn = new PipedInputStream(PIPE_SIZE);
        connect(pipeIn);

        reader = new BufferedReader(new InputStreamReader(pipeIn));
        writer = new BufferedWriter(new OutputStreamWriter(this));

        thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    public void write(String line) throws IOException {
        writer.write(line.endsWith("\n") ? line : line+"\n");
        writer.flush();
    }

    public void error(String line) {
        if (error.get()) {
            log.warn("error("+line+") ignored, error already set");
            return;
        }
        closed.set(true);
        error.set(true);
        background(this::close);
        setCurrentTick(newTick(getErrorMessageKey(line), line));
    }

    private NodeProgressMeterTick newTick(String messageKey, String line) {
        return new NodeProgressMeterTick()
                .setAccount(nn.getAccount())
                .setNetwork(nn.getNetwork())
                .setMessageKey(messageKey)
                .setDetails(line);
    }

    public void reset() {
        if (closed.get()) die("reset: progress meter is closed, cannot reset");
        error.set(false);
        tickPos = standardTicks.size();
        _setCurrentTick(lastStandardTick);
    }

    @Override public void run() {
        String line;
        try {
            while ((line = reader.readLine()) != null && !closed.get()) {
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
            log.info("run: "+e);
        }
    }

    public void setCurrentTick(NodeProgressMeterTick tick) { _setCurrentTick(tick, false); }
    public void _setCurrentTick(NodeProgressMeterTick tick) { _setCurrentTick(tick, true); }

    public void _setCurrentTick(NodeProgressMeterTick tick, boolean allowForce) {
        final String json = json(tick, COMPACT_MAPPER);
        if (!allowForce && closed.get()) {
            log.warn("setCurrentTick (closed, not setting): "+json);
            return;
        }
        log.info("setCurrentTick: "+ json);
        redis.set(getProgressMeterKey(key, nn.getAccount()), json, EX, TICK_REDIS_EXPIRATION);
    }

    public static String getProgressMeterKey(String key, String accountUuid) { return accountUuid+":"+key; }
    public static String getProgressMeterPrefix(String accountUuid) { return accountUuid+":"; }

    public static final long THREAD_KILL_TIMEOUT = SECONDS.toMillis(5);

    @Override public void close() {
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
        closed.set(true);
        background(this::close);
        _setCurrentTick(new NodeProgressMeterTick()
                .setNetwork(nn.getNetwork())
                .setAccount(nn.getAccount())
                .setMessageKey(METER_COMPLETED)
                .setPercent(100));
    }

}