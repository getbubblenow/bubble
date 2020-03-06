/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;

public class AppDataField extends EntityFieldConfig {

    @Getter @Setter private Boolean customFormat;
    @Getter @Setter private String when;
    @Getter @Setter private Boolean truncate;

}
