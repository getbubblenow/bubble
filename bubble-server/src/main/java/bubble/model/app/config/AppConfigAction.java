/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class AppConfigAction {

    @Getter @Setter private String name;
    @Getter @Setter private AppConfigScope scope = AppConfigScope.item;
    @Getter @Setter private String when;
    @Getter @Setter private String view;
    @Getter @Setter private String dataView;
    @Getter @Setter private String successView;
    @Getter @Setter private String successMessage;
    @Getter @Setter private Integer index = 0;

    @Getter @Setter private String[] params;
    public boolean hasParams () { return !empty(params); }

    @Getter @Setter private String button;
    public boolean hasButton () { return !empty(button); }

}
