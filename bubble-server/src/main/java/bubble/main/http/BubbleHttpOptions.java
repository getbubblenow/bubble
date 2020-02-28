/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.main.http;

import bubble.main.BubbleApiOptionsBase;
import lombok.Getter;
import lombok.Setter;
import org.kohsuke.args4j.Option;

public class BubbleHttpOptions extends BubbleApiOptionsBase {

    public static final String USAGE_URL = "URL to request";
    public static final String OPT_URL = "-U";
    public static final String LONGOPT_URL= "--url";
    @Option(name=OPT_URL, aliases=LONGOPT_URL, usage=USAGE_URL)
    @Getter @Setter private String url;

    public static final String USAGE_RAW = "Raw response: do not parse as JSON";
    public static final String OPT_RAW = "-R";
    public static final String LONGOPT_RAW= "--raw";
    @Option(name=OPT_RAW, aliases=LONGOPT_RAW, usage=USAGE_RAW)
    @Getter @Setter private boolean raw = false;

    public static final String USAGE_INCLUDE_NOLOGIN = "Do not login first. Default is true.";
    public static final String OPT_INCLUDE_NOLOGIN = "-L";
    public static final String LONGOPT_INCLUDE_NOLOGIN = "--no-login";
    @Option(name=OPT_INCLUDE_NOLOGIN, aliases=LONGOPT_INCLUDE_NOLOGIN, usage=USAGE_INCLUDE_NOLOGIN)
    @Getter @Setter private Boolean noLogin = null;

    @Override public boolean requireAccount() { return noLogin == null || !noLogin; }

}
