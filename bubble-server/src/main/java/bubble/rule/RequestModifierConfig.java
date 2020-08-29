/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.collection.NameAndValue;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class RequestModifierConfig {

    @Getter @Setter private String siteJsTemplate;
    @Getter @Setter private NameAndValue[] additionalJsTemplates;

    @Getter @Setter private String insertionRegex;
    @Getter @Setter private String scriptOpenNonce;
    @Getter @Setter private String scriptOpenNoNonce;
    @Getter @Setter private String scriptClose;

    @Getter @Setter private BubbleRegexReplacement[] additionalRegexReplacements;
    public boolean hasAdditionalRegexReplacements () { return !empty(additionalRegexReplacements); }

    @Getter @Setter private BubbleAlternateRegexReplacement[] alternateRegexReplacements;
    public boolean hasAlternateRegexReplacements () { return !empty(alternateRegexReplacements); }

}
