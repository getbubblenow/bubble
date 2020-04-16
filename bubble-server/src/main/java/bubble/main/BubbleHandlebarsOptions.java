/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.main.BaseMainOptions;
import org.kohsuke.args4j.Option;

import java.io.File;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class BubbleHandlebarsOptions extends BaseMainOptions {

    public static final String USAGE_ADDITIONAL_CTX = "Additional context JSON file";
    public static final String OPT_ADDITIONAL_CTX = "-j";
    public static final String LONGOPT_ADDITIONAL_CTX = "--context-json";
    @Option(name=OPT_ADDITIONAL_CTX, aliases=LONGOPT_ADDITIONAL_CTX, usage=USAGE_ADDITIONAL_CTX)
    @Getter @Setter private File additionalContext;
    public boolean hasAdditionalContext() { return !empty(additionalContext); }

    public static final String USAGE_ADDITIONAL_CTX_NAME = "Name of handlebars var for context JSON file";
    public static final String OPT_ADDITIONAL_CTX_NAME = "-n";
    public static final String LONGOPT_ADDITIONAL_CTX_NAME = "--context-name";
    @Option(name=OPT_ADDITIONAL_CTX_NAME, aliases=LONGOPT_ADDITIONAL_CTX_NAME, usage=USAGE_ADDITIONAL_CTX_NAME)
    @Getter @Setter private String additionalContextName = "json";

    public static final String USAGE_ADDITIONAL_CTX_CLASS = "Name of Java class for handlebars context";
    public static final String OPT_ADDITIONAL_CTX_CLASS = "-c";
    public static final String LONGOPT_ADDITIONAL_CTX_CLASS = "--context-class";
    @Option(name=OPT_ADDITIONAL_CTX_CLASS, aliases=LONGOPT_ADDITIONAL_CTX_CLASS, usage=USAGE_ADDITIONAL_CTX_CLASS)
    @Getter @Setter private String additionalContextClass = JsonNode.class.getName();

}
