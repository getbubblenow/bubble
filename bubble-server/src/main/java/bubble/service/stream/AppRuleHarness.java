/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.service.stream;

import bubble.model.app.AppMatcher;
import bubble.model.app.RuleDriver;
import bubble.model.app.AppRule;
import bubble.rule.AppRuleDriver;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.collection.HasPriority;

public class AppRuleHarness implements Comparable<AppRuleHarness> {

    @Getter private final AppMatcher matcher;
    @Getter private final AppRule rule;
    @Getter @Setter private RuleDriver ruleDriver;
    @Getter @Setter private AppRuleDriver driver;

    public AppRuleHarness(AppMatcher matcher, AppRule rule) {
        this.matcher = matcher;
        this.rule = rule;
    }

    @Override public int compareTo(AppRuleHarness other) { return HasPriority.compare(other.getRule(), getRule()); }

}
