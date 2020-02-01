package bubble.model.app.config;

import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class AppConfigView {

    @Getter @Setter private String name;
    @Getter @Setter private AppConfigScope scope;

    @Getter @Setter private Boolean root = true;
    public boolean root() { return root != null && root; }

    @Getter @Setter private String[] columns;
    public boolean hasColumns () { return !empty(columns); }

    @Getter @Setter private AppDataField[] fields;
    public boolean hasFields () { return !empty(fields); }

    @Getter @Setter private AppConfigAction[] actions;
    public boolean hasActions () { return !empty(actions); }

}
