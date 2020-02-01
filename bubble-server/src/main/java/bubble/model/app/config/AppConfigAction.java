package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class AppConfigAction {

    @Getter @Setter private String name;
    @Getter @Setter private AppConfigScope scope = AppConfigScope.item;
    @Getter @Setter private String when;
    @Getter @Setter private String view;

    @Getter @Setter private AppDataField[] params;
    public boolean hasParams () { return !empty(params); }

    @Getter @Setter private String button;
    public boolean hasButton () { return !empty(button); }

}
