package bubble.model.app;

import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.model.app.AppDataPresentation.none;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

public class AppDataConfig {

    @Getter @Setter private AppDataPresentation presentation = none;

    @Getter @Setter private String driverClass;
    public boolean hasDriverClass () { return driverClass != null; }

    @Getter @Setter private AppDataField[] fields;
    public boolean hasFields () { return !empty(fields); }

    @Getter @Setter private EntityFieldConfig[] params;
    public boolean hasParams () { return !empty(params); }

    @Getter @Setter private AppDataAction[] actions;
    public boolean hasActions () { return !empty(actions); }

    public AppDataAction getAction(String actionName) {
        if (!hasActions()) return null;
        for (AppDataAction a : getActions()) if (a.getName().equalsIgnoreCase(actionName)) return a;
        return null;
    }

    @Getter @Setter private AppDataView[] views;
    public boolean hasViews () { return !empty(views); }

    public AppDataView getView(String viewName) {
        if (!hasViews()) return null;
        for (AppDataView v : getViews()) if (v.getName().equalsIgnoreCase(viewName)) return v;
        return null;
    }

    private final Map<String, AppDataDriver> DRIVER_CACHE = new ConcurrentHashMap<>();
    public AppDataDriver getDriver(BubbleConfiguration configuration) {
        return DRIVER_CACHE.computeIfAbsent(getDriverClass(), c -> configuration.autowire(instantiate(c)));
    }

}
