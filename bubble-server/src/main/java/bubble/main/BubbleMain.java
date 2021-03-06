/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main;

import bubble.cloud.dns.godaddy.GoDaddyDnsCleanerMain;
import bubble.main.http.BubbleHttpDeleteMain;
import bubble.main.http.BubbleHttpGetMain;
import bubble.main.http.BubbleHttpPostMain;
import bubble.main.http.BubbleHttpPutMain;
import bubble.server.BubbleServer;
import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.util.main.ConstMain;
import org.cobbzilla.util.main.FileHeaderMain;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.main.MainBase;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Map;
import java.util.TreeSet;

import static org.cobbzilla.util.collection.ArrayUtil.shift;
import static org.cobbzilla.util.main.BaseMainOptions.LONGOPT_HELP;
import static org.cobbzilla.util.main.BaseMainOptions.OPT_HELP;

public class BubbleMain {

    private static final Map<String, Class<? extends MainBase>> mainClasses = MapBuilder.build(new Object[][]{
            {"server", BubbleServer.class},
            {"model", BubbleModelMain.class},
            {"script", BubbleScriptMain.class},
            {"get", BubbleHttpGetMain.class},
            {"post", BubbleHttpPostMain.class},
            {"put", BubbleHttpPutMain.class},
            {"delete", BubbleHttpDeleteMain.class},
            {"handlebars", BubbleHandlebarsMain.class},
            {"crypt", CryptMain.class},
            {"rekey", RekeyDatabaseMain.class},
            {"generate-algo-conf", GenerateAlgoConfMain.class},
            {"const", ConstMain.class},
            {"file-header", FileHeaderMain.class},
            {"gd-cleanup", GoDaddyDnsCleanerMain.class}
    });

    public static void main(String[] args) throws Exception {

        if (args.length == 0 || (
                args.length == 1 && ( args[0].equals(OPT_HELP) || args[0].equals(LONGOPT_HELP) )
        )) {
            die(noCommandProvided());
        }

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // extract command
        final String command = args[0];
        final Class mainClass = mainClasses.get(command.toLowerCase());
        if (mainClass == null) die(noCommandProvided());

        final String[] newArgs = shift(args);

        // invoke "real" main
        mainClass.getMethod("main", String[].class).invoke(null, (Object) newArgs);
    }

    private static String noCommandProvided() {
        return "No command provided, use one of\n"+ StringUtil.toString(new TreeSet<>(mainClasses.keySet()), "\n");
    }

    private static void die(String s) {
        System.out.println(s);
        System.exit(1);
    }

}
