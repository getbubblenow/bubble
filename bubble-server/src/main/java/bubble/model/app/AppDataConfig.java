package bubble.model.app;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @Getter @Setter private String[] fields;
    public boolean hasFields () { return !empty(fields); }

    @Getter @Setter private EntityFieldConfig[] params;

    @Getter @Setter private AppDataAction[] actions;

    @Getter @Setter private AppDataView[] views;
    public boolean hasViews () { return !empty(views); }

    private final Map<String, AppDataDriver> DRIVER_CACHE = new ConcurrentHashMap<>();
    @JsonIgnore @Getter(lazy=true) private final AppDataDriver driver = DRIVER_CACHE.computeIfAbsent(getDriverClass(), c -> (AppDataDriver) instantiate(c));

}
