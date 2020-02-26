/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.service.dbfilter;

import bubble.main.RekeyDatabaseMain;
import bubble.main.RekeyDatabaseOptions;
import bubble.main.rekey.RekeyOptions;
import bubble.main.rekey.RekeyReaderMain;
import bubble.model.account.Account;
import bubble.model.bill.BubblePlanApp;
import bubble.model.cloud.BubbleNetwork;
import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.jdbc.DbDumpMode;
import org.cobbzilla.util.network.PortPicker;
import org.cobbzilla.util.system.Command;
import org.cobbzilla.util.system.CommandResult;
import org.cobbzilla.util.system.CommandShell;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.wizard.server.config.PgRestServerConfiguration.ENV_PGPASSWORD;

@Service @Slf4j
public class DatabaseFilterService {

    public static final long DB_FILTER_CHECK = SECONDS.toMillis(1);
    public static final long DB_FILTER_TIMEOUT = SECONDS.toMillis(120);
    public static final long THREAD_KILL_TIMEOUT = SECONDS.toMillis(10);

    public static final String ENV_OLD_DB_KEY = "OLD_DB_KEY";
    public static final String ENV_NEW_DB_KEY = "NEW_DB_KEY";

    @Autowired private BubbleConfiguration configuration;

    public String copyDatabase(boolean fork,
                               BubbleNetwork network,
                               BubbleNode node,
                               Account account,
                               List<BubblePlanApp> planApps,
                               File pgDumpFile) {
        final String dbName = ("bubble_slice_"+randomAlphanumeric(8)+"_"+now()).toLowerCase();
        log.info("copyDatabase: starting with dbName: "+dbName);

        final DatabaseConfiguration dbConfig = configuration.getDatabase();
        final String dbUser = dbConfig.getUser();
        final String dbPass = dbConfig.getPassword();
        final String newKey = randomUUID().toString();
        Thread reader = null;
        Thread writer = null;

        try {
            // Create a temporary database
            @Cleanup final TempDir temp = new TempDir();

            log.info("copyDatabase: creating new database: "+dbName);
            final CommandResult createdb = pgExec("createdb", dbName);
            if (!createdb.isZeroExitStatus()) return die("copyDatabase: error creating new database: "+dbName);

            // Write schema to new database
            final File schema = new File(temp, "schema.sql");
            log.info("copyDatabase: dumping schema for current database to file: "+abs(schema));
            try (InputStream in = new FileInputStream(configuration.pgDump(schema, DbDumpMode.schema))) {
                log.info("copyDatabase: writing dumped schema to new database "+dbName);
                final CommandResult schemaResult = pgExec("psql", dbName, in, null);
                if (!schemaResult.isZeroExitStatus()) return die("copyDatabase: error populating schema for "+dbName+": "+schemaResult);
            }

            log.info("copyDatabase: copying/filtering data into new database: "+dbName+", "+planApps.size()+" apps enabled") ;
            final int port = PortPicker.pickOrDie();

            final Map<String, String> env = new HashMap<>(configuration.pgEnv());
            env.put(ENV_OLD_DB_KEY, dbConfig.getEncryptionKey());
            env.put(ENV_NEW_DB_KEY, newKey);

            // start a RekeyReader to send objects to RekeyWriter.
            // the RekeyReader will run in-process and receive objects from this method, instead of doing its own queries
            final AtomicReference<Exception> readerError = new AtomicReference<>();
            final RekeyOptions readerOptions = new RekeyOptions() {
                @Override public Map<String, String> getEnv() { return env; }
            }
                    .setDatabase(dbConfig.getDatabaseName())
                    .setDbUser(dbUser)
                    .setDbPass(dbPass)
                    .setKey("@" + ENV_OLD_DB_KEY)
                    .setPort(port);
            reader = new RekeyReaderMain() {
                @Override public RekeyOptions getOptions() { return readerOptions; }
                @Override protected Iterator<Identifiable> getEntityProducer(BubbleConfiguration fromConfig, AtomicReference<Exception> error) {
                    return fork
                            ? new FullEntityIterator(configuration, network, readerError)
                            : new FilteredEntityIterator(configuration, account, network, node, planApps, readerError);
                }
            }.runInBackground(readerError::set);

            // start a RekeyWriter to pull objects from RekeyReader
            final AtomicReference<CommandResult> writeResult = new AtomicReference<>();
            final RekeyDatabaseOptions writerOptions = new RekeyDatabaseOptions()
                    .setDbUser(dbUser)
                    .setDbPass("@" + ENV_PGPASSWORD)
                    .setFromDb("_ignored_")
                    .setFromKey("_ignored_")
                    .setToDb(dbName)
                    .setToKey("@" + ENV_NEW_DB_KEY)
                    .setPort(port)
                    .setJar(abs(configuration.getBubbleJar()));
            writer = RekeyDatabaseMain.runWriter(writerOptions, writeResult, env);

            final long start = now();
            while ((reader.isAlive() || writer.isAlive()) && now() - start < DB_FILTER_TIMEOUT) {
                reader.join(DB_FILTER_CHECK);
                writer.join(DB_FILTER_CHECK);
                if (readerError.get() != null) die("copyDatabase: reader error: "+shortError(readerError.get()), readerError.get());
                if (writeResult.get() != null && writeResult.get().hasException()) {
                    die("copyDatabase: writer error: "+writeResult.get().getStderr()+"\n"+shortError(writeResult.get().getException()));
                }
            }
            if (reader.isAlive() || writer.isAlive()) {
                log.error("copyDatabase: reader/writer taking too long, stopping them (dbName="+dbName+")");
                stopThread(reader, node, "reader");
                stopThread(writer, node, "writer");
            }
            if (writeResult.get() == null) {
                return die("copyDatabase: writer never set CommandResult (dbName="+dbName+")");
            }
            if (!writeResult.get().isZeroExitStatus()) {
                return die("copyDatabase: writer exited with an error (dbName="+dbName+"): "+writeResult.get());
            }

            // dump new DB
            log.info("copyDatabase: dumping new database: "+dbName);
            try (OutputStream out = new GZIPOutputStream(new FileOutputStream(pgDumpFile))) {
                final CommandResult pgDumpResult = pgExec("pg_dump", dbName, null, out);
                if (!pgDumpResult.isZeroExitStatus()) return die("copyDatabase: error dumping new database "+dbName+": "+pgDumpResult);
            }
            log.info("copyDatabase: completed");
            return newKey;

        } catch (Exception e) {
            return die("copyDatabase: error: "+e, e);

        } finally {
            if (reader != null) stopThread(reader, node, "reader");
            if (writer != null) stopThread(writer, node, "writer");
            try {
                final CommandResult dropdb = pgExec("dropdb", dbName);
                if (!dropdb.isZeroExitStatus()) log.warn("copyDatabase: error dropping database: "+dropdb);
            } catch (Exception e) {
                log.warn("copyDatabase: error dropping database: "+dbName+": "+e);
            }
        }
    }

    public CommandResult pgExec(String command, String dbName) throws IOException {
        return pgExec(command, dbName, null, null);
    }

    public CommandResult pgExec(String command, String dbName, InputStream in, OutputStream out) throws IOException {
        final Command pgCommand = new Command(new CommandLine(command)
                .addArguments(configuration.pgOptions(dbName)))
                .setEnv(configuration.pgEnv())
                .setStdin(in)
                .setOut(out)
                .setCopyToStandard(log.isDebugEnabled());
        log.info("copyDatabase: running "+command+" script='" + pgCommand.getCommandLine()+"'");
        final CommandResult result = CommandShell.exec(pgCommand);
        log.info("copyDatabase: running "+command+" "+dbName+": " + pgCommand.getCommandLine() + "', result=" + result.getExitStatus());
        return result;
    }

    private boolean stopThread(Thread t, BubbleNode node, String name) {
        if (terminate(t, THREAD_KILL_TIMEOUT)) {
            log.info("copyDatabase: "+name+" thread finished! we are OK.");
            return true;
        } else {
            return die("copyDatabase: "+name+" error copying database for node: "+node.getUuid());
        }
    }
}
