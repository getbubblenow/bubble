package bubble.main;

import bubble.server.BubbleDbFilterServer;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.main.DbMainOptions;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import static bubble.server.BubbleServer.API_CONFIG_YML;
import static org.cobbzilla.util.daemon.ZillaRuntime.readStdin;

public class CryptOptions extends DbMainOptions {

    public static final String USAGE_FUNC = "Function. Default is decrypt.";
    public static final String OPT_FUNC = "-f";
    public static final String LONGOPT_FUNC= "--function";
    @Option(name=OPT_FUNC, aliases=LONGOPT_FUNC, usage=USAGE_FUNC)
    @Getter @Setter private CryptOperation operation = CryptOperation.decrypt;

    public enum CryptOperation { encrypt, decrypt }

    @Argument(usage="value to encrypt or decrypt. Use '.' to read from stdin", required=true)
    @Setter private String value;
    public String getValue () { return value.equals(".") ? readStdin() : value; }

    @Override public String getServerClass() { return BubbleDbFilterServer.class.getName(); }
    @Override public String getConfigPath() { return API_CONFIG_YML; }
    @Override public String getDefaultCryptEnvVar() { return "BUBBLE_DB_ENCRYPTION_KEY"; }

}
