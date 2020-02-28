/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.rule;

import lombok.Getter;
import lombok.Setter;

public class DriverUserConfig {

    @Getter @Setter private UserConfigField[] fields;

    public UserConfigField getField(String name) {
        if (fields == null || fields.length == 0) return null;
        for (UserConfigField f : fields) if (f.getName().equals(name)) return f;
        return null;
    }

}
