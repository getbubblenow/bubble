/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main.rekey;

import bubble.server.BubbleConfiguration;
import bubble.server.BubbleDbFilterServer;
import bubble.service.dbfilter.EndOfEntityStream;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.main.BaseMain;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.server.RestServerHarness;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.cobbzilla.util.json.JsonUtil.COMPACT_MAPPER;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.network.NetworkUtil.IPv4_LOCALHOST;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class RekeyWriterMain extends BaseMain<RekeyOptions> {

    public static final long RETRY_DELAY = SECONDS.toMillis(5);

    public static void main (String[] args) { main(RekeyWriterMain.class, args); }

    @Override protected void run() throws Exception {

        final boolean debugEnabled = log.isDebugEnabled();
        final RekeyOptions options = getOptions();
        if (debugEnabled) out("WRITER: options="+json(options, COMPACT_MAPPER));

        final RestServerHarness<BubbleConfiguration, BubbleDbFilterServer> toHarness = options.getServer();
        final BubbleConfiguration toConfig = toHarness.getConfiguration();

        IdentifiableBase.getEnforceNullUuidOnCreate().set(false);
        AbstractCRUDDAO.getRawMode().set(true);
        final var daoMap = new HashMap<Class<? extends Identifiable>, DAO>();

        while (true) {
            try {
                @Cleanup final Socket clientSocket = new Socket(IPv4_LOCALHOST, options.getPort());
                @Cleanup BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String line;
                if (debugEnabled) out("WRITER: reading first object from reader...");
                while ((line = inFromServer.readLine()) != null) {
                    if (debugEnabled) out("WRITER<<< received json: " + line);
                    final Identifiable entity = Identifiable.deserialize(line);
                    if (entity instanceof EndOfEntityStream) break;
                    if (debugEnabled) out("WRITER: deserialized json to: " + entity);

                    final var dao = daoMap.computeIfAbsent(entity.getClass(),
                                                           entityClass -> toConfig.getDaoForEntityClass(entityClass));
                    dao.create(entity);
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
