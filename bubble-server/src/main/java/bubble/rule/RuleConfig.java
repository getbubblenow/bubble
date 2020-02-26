/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.rule;

public interface RuleConfig {

    String getApp();
    String getRule();
    String getMatcher();
    String getDriver();

}
