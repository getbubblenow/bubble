/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.IPv4_LOCALHOST;

@Slf4j
public class RekeyReaderMain extends BaseMain<RekeyOptions> {

    public static void main (String[] args) { main(RekeyReaderMain.class, args); }

    @Override protected void run() throws Exception {

        final boolean debugEnabled = log.isDebugEnabled();
        final RekeyOptions options = getOptions();
        if (debugEnabled) out("READER: options="+json(options, COMPACT_MAPPER));

        @Cleanup final ServerSocket sock = new ServerSocket(options.getPort(), 10, InetAddress.getByName(IPv4_LOCALHOST));
        out("READER: awaiting connection from WRITER...");
        @Cleanup final Socket connectionSocket = sock.accept();
        out("READER: connection established");
        @Cleanup final DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());

        final RestServerHarness<BubbleConfiguration, BubbleDbFilterServer> fromHarness = getOptions().getServer();
        final BubbleConfiguration fromConfig = fromHarness.getConfiguration();
        final AtomicReference<Exception> error = new AtomicReference<>();

        if (debugEnabled) out("READER: creating entity producer...");
        final Iterator<Identifiable> producer = getEntityProducer(fromConfig, error);
        if (debugEnabled) out("READER: created entity producer, iterating...");
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

    protected Iterator<Identifiable> getEntityProducer(BubbleConfiguration fromConfig, AtomicReference<Exception> error) {
        return new FullEntityIterator(fromConfig, null, null, null, error);
    }

}
