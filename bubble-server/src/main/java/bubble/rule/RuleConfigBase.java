/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.rule;

import lombok.Getter;
import lombok.Setter;

public class RuleConfigBase implements RuleConfig {

    @Getter @Setter protected String app;
    @Getter @Setter protected String driver;
    @Getter @Setter protected String rule;
    @Getter @Setter protected String matcher;

}
