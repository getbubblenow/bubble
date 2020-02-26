/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.collection.HasPriority;

public class AppDataView implements HasPriority {

    @Getter @Setter private AppDataPresentation presentation = AppDataPresentation.app;
    @Getter @Setter private AppDataViewLayout layout = AppDataViewLayout.table;
    @Getter @Setter private String name;
    @Getter @Setter private Integer priority = 0;

}
