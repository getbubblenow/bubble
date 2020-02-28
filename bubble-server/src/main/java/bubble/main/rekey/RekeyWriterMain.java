/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.main.rekey;

import bubble.server.BubbleConfiguration;
import bubble.server.BubbleDbFilterServer;
import bubble.service.dbfilter.EndOfEntityStream;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.main.BaseMain;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.server.RestServerHarness;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class RekeyWriterMain extends BaseMain<RekeyOptions> {

    public static final long RETRY_DELAY = SECONDS.toMillis(5);

    public static void main (String[] args) { main(RekeyWriterMain.class, args); }

    @Override protected void run() throws Exception {

        final RekeyOptions options = getOptions();
        // log.info("run: options=\n"+json(options));

        final RestServerHarness<BubbleConfiguration, BubbleDbFilterServer> toHarness = options.getServer();
        final BubbleConfiguration toConfig = toHarness.getConfiguration();

        IdentifiableBase.getEnforceNullUuidOnCreate().set(false);
        AbstractCRUDDAO.getRawMode().set(true);
        final boolean debugEnabled = log.isDebugEnabled();
        while (true) {
            try {
                @Cleanup final Socket clientSocket = new Socket("127.0.0.1", options.getPort());
                @Cleanup BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String line;
                while ((line = inFromServer.readLine()) != null) {
                    if (debugEnabled) out("WRITER<<< received json: " + line);
                    final Identifiable entity = Identifiable.deserialize(line);
                    if (entity instanceof EndOfEntityStream) break;
                    if (debugEnabled) out("WRITER: deserialized json to: " + entity);
                    toConfig.getDaoForEntityClass(entity.getClass()).create(entity);
                }
                out("WRITER: complete");
                return;
            } catch (SocketException e) {
                err("WRITER SocketException: "+e);
                throw e;
            } catch (Exception e) {
                err("WRITER error (sleeping then retrying): "+e+"\n"+getStackTrace(e));
                sleep(RETRY_DELAY);
            }
        }
    }
}
