/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
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
