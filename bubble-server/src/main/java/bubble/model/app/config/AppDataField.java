/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;

public class AppDataField extends EntityFieldConfig {

    @Getter @Setter private Boolean customFormat;
    @Getter @Setter private String when;

}
