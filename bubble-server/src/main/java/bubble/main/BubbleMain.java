package bubble.main;

import bubble.main.http.BubbleHttpDeleteMain;
import bubble.main.http.BubbleHttpGetMain;
import bubble.main.http.BubbleHttpPostMain;
import bubble.main.http.BubbleHttpPutMain;
import bubble.server.BubbleServer;
import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.util.main.ConstMain;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.main.MainBase;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Map;
import java.util.TreeSet;

import static org.cobbzilla.util.collection.ArrayUtil.shift;

public class BubbleMain {

    private static Map<String, Class<? extends MainBase>> mainClasses = MapBuilder.build(new Object[][]{
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
            {"const", ConstMain.class}
    });

    public static void main(String[] args) throws Exception {

        if (args.length == 0) die(noCommandProvided());

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
