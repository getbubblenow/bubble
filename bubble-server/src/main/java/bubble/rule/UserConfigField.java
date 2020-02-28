/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
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
