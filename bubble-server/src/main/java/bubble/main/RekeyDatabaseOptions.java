/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.main;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.main.BaseMainOptions;
import org.cobbzilla.util.network.PortPicker;
import org.kohsuke.args4j.Option;

@Accessors(chain=true)
public class RekeyDatabaseOptions extends BaseMainOptions {

    public static final String USAGE_DB_USER = "Database username";
    public static final String OPT_DB_USER = "-U";
    public static final String LONGOPT_DB_USER= "--db-user";
    @Option(name=OPT_DB_USER, aliases=LONGOPT_DB_USER, usage=USAGE_DB_USER, required=true)
    @Getter @Setter private String dbUser;

    public static final String USAGE_DB_PASS = "Database password";
    public static final String OPT_DB_PASS = "-P";
    public static final String LONGOPT_DB_PASS= "--db-password";
    @Option(name=OPT_DB_PASS, aliases=LONGOPT_DB_PASS, usage=USAGE_DB_PASS, required=true)
    @Getter @Setter private String dbPass;
    public String getDbPassValue () { return keyValue(getDbPass(), "dbPass"); }

    public static final String USAGE_FROM_DB = "Database to read from";
    public static final String OPT_FROM_DB = "-F";
    public static final String LONGOPT_FROM_DB= "--from-db";
    @Option(name=OPT_FROM_DB, aliases=LONGOPT_FROM_DB, usage=USAGE_FROM_DB, required=true)
    @Getter @Setter private String fromDb;

    public static final String USAGE_FROM_KEY = "Key for from-db";
    public static final String OPT_FROM_KEY = "-D";
    public static final String LONGOPT_FROM_KEY= "--from-key";
    @Option(name=OPT_FROM_KEY, aliases=LONGOPT_FROM_KEY, usage=USAGE_FROM_KEY, required=true)
    @Getter @Setter private String fromKey;
    public String getFromKeyValue () { return keyValue(getFromKey(), "fromKey"); }

    public static final String USAGE_TO_DB = "Database to write to";
    public static final String OPT_TO_DB = "-T";
    public static final String LONGOPT_TO_DB= "--to-db";
    @Option(name=OPT_TO_DB, aliases=LONGOPT_TO_DB, usage=USAGE_TO_DB, required=true)
    @Getter @Setter private String toDb;

    public static final String USAGE_TO_KEY = "Key for to-db";
    public static final String OPT_TO_KEY = "-E";
    public static final String LONGOPT_TO_KEY= "--to-key";
    @Option(name=OPT_TO_KEY, aliases=LONGOPT_TO_KEY, usage=USAGE_TO_KEY, required=true)
    @Getter @Setter private String toKey;
    public String getToKeyValue () { return keyValue(getToKey(), "toKey"); }

    public static final String USAGE_PORT = "Port to communicate on";
    public static final String OPT_PORT = "-p";
    public static final String LONGOPT_PORT= "--port";
    @Option(name=OPT_PORT, aliases=LONGOPT_PORT, usage=USAGE_PORT)
    @Getter @Setter private int port = PortPicker.pickOrDie();

    public static final String USAGE_JAR = "Path to bubble jar file";
    public static final String OPT_JAR = "-J";
    public static final String LONGOPT_JAR= "--jar";
    @Option(name=OPT_JAR, aliases=LONGOPT_JAR, usage=USAGE_JAR, required=true)
    @Getter @Setter private String jar;

    public static final String USAGE_READER_DEBUG = "Reader debug port";
    public static final String OPT_READER_DEBUG = "-R";
    public static final String LONGOPT_READER_DEBUG= "--reader-debug-port";
    @Option(name=OPT_READER_DEBUG, aliases=LONGOPT_READER_DEBUG, usage=USAGE_READER_DEBUG)
    @Getter @Setter private Integer readerDebugPort;

    public static final String USAGE_WRITER_DEBUG = "Writer debug port";
    public static final String OPT_WRITER_DEBUG = "-W";
    public static final String LONGOPT_WRITER_DEBUG= "--writer-debug-port";
    @Option(name=OPT_WRITER_DEBUG, aliases=LONGOPT_WRITER_DEBUG, usage=USAGE_WRITER_DEBUG)
    @Getter @Setter private Integer writerDebugPort;

}
