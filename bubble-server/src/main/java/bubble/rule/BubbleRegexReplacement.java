/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule;

import bubble.resources.stream.FilterHttpRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.cobbzilla.util.io.regex.RegexReplacementFilter;

import static bubble.rule.AbstractAppRuleDriver.NONCE_VAR;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@ToString
public class BubbleRegexReplacement {

    @Getter @Setter private String insertionRegex;
    @Getter @Setter private String replacement;
    public boolean hasReplacement () { return !empty(replacement); }

    public RegexReplacementFilter regexFilter(FilterHttpRequest filterRequest, String replacement) {
        return new RegexReplacementFilter(getInsertionRegex(), (hasReplacement() ? getReplacement() : replacement).replace(NONCE_VAR, filterRequest.getScriptNonce()));
    }

}
