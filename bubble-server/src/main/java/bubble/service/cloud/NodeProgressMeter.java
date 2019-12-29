package bubble.service.cloud;

import bubble.notify.NewNodeNotification;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.wizard.cache.redis.RedisService;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static bubble.service.cloud.NodeProgressMeterConstants.*;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.daemon.ZillaRuntime.terminate;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
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

        standardTicks = getStandardTicks();
        lastStandardTick = standardTicks.get(standardTicks.size()-1);

        ticks = new ArrayList<>(standardTicks);
        final NodeProgressMeterTick[] installTicks = json(stream2string(TICKS_JSON), NodeProgressMeterTick[].class);
        for (NodeProgressMeterTick tick : installTicks) {
            tick.setAccount(nn.getAccount()).relativizePercent(lastStandardTick.getPercent());
        }
        ticks.addAll(asList(installTicks));

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
        error.set(true);
        close();
        setCurrentTick(errorTick(getErrorMessageKey(line), line));
    }

    private NodeProgressMeterTick errorTick(String messageKey, String line) {
        return new NodeProgressMeterTick()
                .setAccount(nn.getAccount())
                .setMessageKey(messageKey)
                .setDetails(line);
    }

    public void reset() {
        error.set(false);
        tickPos = standardTicks.size();
        setCurrentTick(lastStandardTick);
    }

    @Override public void run() {
        String line;
        try {
            final File file = new File("/tmp/node_launch_progress.txt");
            while ((line = reader.readLine()) != null && !closed.get()) {
                FileUtil.appendFile(file, now()+" : "+line+"\n");
                for (int i=tickPos; i<ticks.size(); i++) {
                    if (ticks.get(i).matches(line)) {
                        if (!error.get() && !closed.get()) setCurrentTick(ticks.get(i));
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

    public void setCurrentTick(NodeProgressMeterTick tick) {
        redis.set(key, json(tick), EX, TICK_REDIS_EXPIRATION);
    }

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
        close();
        setCurrentTick(new NodeProgressMeterTick()
                .setAccount(nn.getAccount())
                .setMessageKey(METER_COMPLETED)
                .setPercent(100));
    }

}
