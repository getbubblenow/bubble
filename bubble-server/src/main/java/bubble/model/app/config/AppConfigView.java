/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class AppConfigView {

    @Getter @Setter private String name;
    @Getter @Setter private AppConfigScope scope;

    @Getter @Setter private Boolean root = false;
    public boolean root() { return root != null && root; }

    @Getter @Setter private String[] fields;
    public boolean hasFields () { return !empty(fields); }

    @Getter @Setter private AppConfigAction[] actions;
    public boolean hasActions () { return !empty(actions); }

}
