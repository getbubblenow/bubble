/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main;

import bubble.main.rekey.RekeyOptions;
import bubble.main.rekey.RekeyReaderMain;
import bubble.main.rekey.RekeyWriterMain;
import org.apache.commons.exec.CommandLine;
import org.cobbzilla.util.main.BaseMain;
import org.cobbzilla.util.system.Command;
import org.cobbzilla.util.system.CommandResult;
import org.cobbzilla.util.system.CommandShell;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.background;
import static org.cobbzilla.util.daemon.ZillaRuntime.getJava;

public class RekeyDatabaseMain extends BaseMain<RekeyDatabaseOptions> {

    public static void main (String[] args) { main(RekeyDatabaseMain.class, args); }

    @Override protected void run() throws Exception {
        final RekeyDatabaseOptions options = getOptions();

        final AtomicReference<CommandResult> readResult = new AtomicReference<>();
        final Thread reader = background(() -> {
            try {
                readResult.set(CommandShell.exec(readerCommand(options, options.getEnv())));
            } catch (Exception e) {
                die("READ ERROR: " + e);
            }
        }, "RekeyDatabaseMain.run.reader");

        final AtomicReference<CommandResult> writeResult = new AtomicReference<>();
        final Thread writer = runWriter(options, writeResult, options.getEnv());

        reader.join(MINUTES.toMillis(10));
        writer.join(MINUTES.toMillis(10));

        out("READ RESULT:");
        out(""+readResult.get());

        out("WRITE RESULT:");
        out(""+writeResult.get());
    }

    public static Thread runWriter(RekeyDatabaseOptions options,
                                   AtomicReference<CommandResult> writeResult,
                                   Map<String, String> env) {
        return background(() -> {
            try {
                writeResult.set(CommandShell.exec(writerCommand(options, env)));
            } catch (Exception e) {
                writeResult.set(new CommandResult(e).setExitStatus(-1));
            }
        }, "RekeyDatabaseMain.runWriter", e -> writeResult.set(new CommandResult(e).setExitStatus(-1)));
    }

    public static Command readerCommand(RekeyDatabaseOptions options, Map<String, String> env) {
        return getCommand(getJava(), RekeyReaderMain.class, options, options.getFromDb(), options.getFromKey(), options.getReaderDebugPort())
                .setEnv(env);
    }

    public static Command writerCommand(RekeyDatabaseOptions options, Map<String, String> env) {
        return getCommand(getJava(), RekeyWriterMain.class, options, options.getToDb(), options.getToKey(), options.getWriterDebugPort())
                .setEnv(env);
    }

    public static Command getCommand(String java,
                                     Class clazz,
                                     RekeyDatabaseOptions options,
                                     String dbName,
                                     String key,
                                     Integer debugPort) {

        CommandLine commandLine = new CommandLine(java)
                .addArgument("-cp")
                .addArgument(options.getJar());

        if (debugPort != null) {
            commandLine = commandLine
                    .addArgument("-Xdebug")
                    .addArgument("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address="+debugPort)
                    .addArgument("-Xnoagent")
                    .addArgument("-Djava.compiler=NONE");
        }

        commandLine = commandLine
                .addArgument(clazz.getName())
                .addArgument(RekeyOptions.LONGOPT_DB)
                .addArgument(dbName)
                .addArgument(RekeyOptions.LONGOPT_DB_USER)
                .addArgument(options.getDbUser())
                .addArgument(RekeyOptions.LONGOPT_DB_PASS)
                .addArgument(options.getDbPass())
                .addArgument(RekeyOptions.LONGOPT_KEY)
                .addArgument(key)
                .addArgument(RekeyOptions.LONGOPT_PORT)
                .addArgument("" + options.getPort());

        return new Command(commandLine).setCopyToStandard(true);
    }
}
