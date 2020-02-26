/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.rule;

import lombok.Getter;
import lombok.Setter;

public class UserConfigField {

    @Getter @Setter private String name;
    @Getter @Setter private String type;
    @Getter @Setter private String description;

    @Getter @Setter private String value;
    public boolean hasValue () { return value != null && value.trim().length() > 0; }

}
