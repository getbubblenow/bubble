/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;

public class AppDataAction {

    @Getter @Setter private String name;
    @Getter @Setter private String when;
    @Getter @Setter private String route;

}
