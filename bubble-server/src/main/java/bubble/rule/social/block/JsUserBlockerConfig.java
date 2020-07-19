/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.social.block;

import bubble.rule.BubbleRegexReplacement;
import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class JsUserBlockerConfig {

    @Getter @Setter private String siteJsTemplate;

    @Getter @Setter private String insertionRegex;
    public boolean hasInsertionRegex () { return !empty(insertionRegex); }

    @Getter @Setter private String scriptOpen;
    public boolean hasScriptOpen () { return !empty(scriptOpen); }

    @Getter @Setter private String scriptClose;
    public boolean hasScriptClose () { return !empty(scriptClose); }

    @Getter @Setter private BubbleRegexReplacement[] additionalRegexReplacements;
    public boolean hasAdditionalRegexReplacements () { return !empty(additionalRegexReplacements); }

}
