/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main.rekey;

import bubble.server.BubbleConfiguration;
import bubble.server.BubbleDbFilterServer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.main.BaseMainOptions;
import org.cobbzilla.wizard.server.RestServerHarness;
import org.kohsuke.args4j.Option;

@NoArgsConstructor @Accessors(chain=true)
public class RekeyOptions extends BaseMainOptions {

    public static final String USAGE_DB = "Database to use";
    public static final String OPT_DB = "-D";
    public static final String LONGOPT_DB = "--database";
    @Option(name=OPT_DB, aliases=LONGOPT_DB, usage=USAGE_DB, required=true)
    @Getter @Setter private String database;

    public static final String USAGE_DB_USER = "Database username";
    public static final String OPT_DB_USER = "-U";
    public static final String LONGOPT_DB_USER = "--db-user";
    @Option(name=OPT_DB_USER, aliases=LONGOPT_DB_USER, usage=USAGE_DB_USER, required=true)
    @Getter @Setter private String dbUser;

    public static final String USAGE_DB_PASS = "Database password";
    public static final String OPT_DB_PASS = "-P";
    public static final String LONGOPT_DB_PASS = "--db-password";
    @Option(name=OPT_DB_PASS, aliases=LONGOPT_DB_PASS, usage=USAGE_DB_PASS)
    @Getter @Setter private String dbPass;
    public String getDbPassValue () { return keyValue(getDbPass(), "dbPass"); }

    public static final String USAGE_KEY = "Key to use";
    public static final String OPT_KEY = "-K";
    public static final String LONGOPT_KEY = "--key";
    @Option(name=OPT_KEY, aliases=LONGOPT_KEY, usage=USAGE_KEY, required=true)
    @Getter @Setter private String key;
    public String getKeyValue () { return keyValue(getKey(), "key"); }

    public static final String USAGE_PORT = "Port to communicate on";
    public static final String OPT_PORT = "-p";
    public static final String LONGOPT_PORT = "--port";
    @Option(name=OPT_PORT, aliases=LONGOPT_PORT, usage=USAGE_PORT, required=true)
    @Getter @Setter private int port;

    @JsonIgnore public RestServerHarness<BubbleConfiguration, BubbleDbFilterServer> getServer() {
        return BubbleConfiguration.dbFilterServer(getDatabase(), getDbUser(), getDbPassValue(), getKeyValue());
    }

}
