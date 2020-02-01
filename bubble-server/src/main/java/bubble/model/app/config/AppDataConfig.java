package bubble.model.app.config;

import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.model.app.config.AppDataPresentation.none;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

public class AppDataConfig {

    @Getter @Setter private AppDataPresentation presentation = none;

    @Getter @Setter private String dataDriver;
    public boolean hasDataDriver () { return dataDriver != null; }

    @Getter @Setter private AppDataField[] fields;
    public boolean hasFields () { return !empty(fields); }

    @Getter @Setter private AppDataParam[] params;
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

    @Getter @Setter private String configDriver;
    public boolean hasConfigDriver () { return configDriver != null; }

    @Getter @Setter private AppDataField[] configFields;
    public boolean hasConfigFields () { return !empty(configFields); }

    @Getter @Setter private AppConfigView[] configViews;
    public boolean hasConfigViews () { return !empty(configViews); }

    public AppConfigView getConfigView(String viewName) {
        if (!hasConfigViews()) return null;
        for (AppConfigView v : getConfigViews()) if (v.getName().equalsIgnoreCase(viewName)) return v;
        return null;
    }

    private final Map<String, AppDataDriver> DATA_DRIVER_CACHE = new ConcurrentHashMap<>();
    public AppDataDriver getDataDriver(BubbleConfiguration configuration) {
        return DATA_DRIVER_CACHE.computeIfAbsent(getDataDriver(), c -> configuration.autowire(instantiate(c)));
    }

    private final Map<String, AppConfigDriver> CONFIG_DRIVER_CACHE = new ConcurrentHashMap<>();
    public AppConfigDriver getConfigDriver(BubbleConfiguration configuration) {
        return CONFIG_DRIVER_CACHE.computeIfAbsent(getConfigDriver(), c -> configuration.autowire(instantiate(c)));
    }
}
