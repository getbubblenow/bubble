/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule;

public interface RuleConfig {

    String getApp();
    String getRule();
    String getMatcher();
    String getDriver();

}
