package bubble.main.rekey;

import bubble.server.BubbleConfiguration;
import bubble.server.BubbleDbFilterServer;
import bubble.service.dbfilter.EndOfEntityStream;
import bubble.service.dbfilter.FullEntityIterator;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.main.BaseMain;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.server.RestServerHarness;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class RekeyReaderMain extends BaseMain<RekeyOptions> {

    public static void main (String[] args) { main(RekeyReaderMain.class, args); }

    private final AtomicReference<Map<String, String>> env = new AtomicReference<>(null);
    public Map<String, String> getEnv() { return env.get() == null ? System.getenv() : env.get(); }
    public RekeyReaderMain setEnv(Map<String, String> env) { this.env.set(env); return this; }

    @Override protected RekeyOptions initOptions() {
        if (env.get() == null) return super.initOptions();
        final Map<String, String> envMap = env.get();
        return new RekeyOptions() {
            @Override public Map<String, String> getEnv() { return envMap; }
        };
    }

    @Override protected void run() throws Exception {

        final RekeyOptions options = getOptions();

        @Cleanup final ServerSocket sock = new ServerSocket(options.getPort());
        out("READER: awaiting connection from WRITER...");
        @Cleanup final Socket connectionSocket = sock.accept();
        out("READER: connection established");
        @Cleanup final DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());

        final RestServerHarness<BubbleConfiguration, BubbleDbFilterServer> fromHarness = getOptions().getServer();
        final BubbleConfiguration fromConfig = fromHarness.getConfiguration();
        final boolean debugEnabled = log.isDebugEnabled();
        final Iterator<Identifiable> producer = getEntityProducer(fromConfig);
        while (producer.hasNext()) {
            final Identifiable from = producer.next();
            if (from instanceof EndOfEntityStream) break;
            try {
                if (debugEnabled) out("READER>>> Sending object: "+from.getClass().getName()+"/"+from.getUuid());
                outToClient.write((from.serialize()+"\n").getBytes());
            } catch (IOException e) {
                die("error writing to socket: "+e);
            }
        }
        outToClient.flush();
        out("READER: complete");
    }

    protected Iterator<Identifiable> getEntityProducer(BubbleConfiguration fromConfig) {
        return new FullEntityIterator(fromConfig);
    }

}
