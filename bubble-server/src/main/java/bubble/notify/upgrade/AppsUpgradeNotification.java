package bubble.notify.upgrade;

import bubble.model.app.AppTemplateEntity;
import bubble.model.app.BubbleApp;
import bubble.model.app.RuleDriver;
import bubble.notify.SynchronousNotification;
import bubble.service.upgrade.AppObjectUpgradeHandler;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.string.StringUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true) @Slf4j
public class AppsUpgradeNotification extends SynchronousNotification {

    @Getter @Setter private RuleDriver[] drivers;
    public boolean hasDrivers() { return !empty(drivers); }

    @Getter @Setter private BubbleApp[] apps;
    public boolean hasApps() { return !empty(apps); }

    @Override protected String getCacheKey() {
        final StringBuilder b = new StringBuilder(AppsUpgradeNotification.class.getName()).append(":");
        if (!empty(drivers)) {
            for (RuleDriver d : drivers) {
                b.append(RuleDriver.class.getName()).append(":").append(d.getName()).append("\n");
            }
        }
        if (!empty(apps)) {
            for (BubbleApp a : apps) {
                b.append(a.cacheKey()).append("\n");
            }
        }
        return sha256_hex(b.toString());
    }

    public void addDriver(RuleDriver driver) { drivers = ArrayUtil.append(drivers, driver); }
    public void addApp(BubbleApp app) { apps = ArrayUtil.append(apps, app); }

    public <T extends AppTemplateEntity> void addAppObjects(List<T> objects) {
        log.info("addAppObjects: adding: "+StringUtil.toString(objects));
        for (T obj : objects) {
            final BubbleApp app = Arrays.stream(apps)
                    .filter(a -> a.getUuid().equals(obj.getApp()))
                    .findFirst()
                    .orElse(null);
            if (app == null) {
                log.warn("addAppObjects: app "+obj.getApp()+" not found for object "+obj.getClass().getSimpleName()+": "+obj.getName());
            } else {
                app.addChild((Class<T>) obj.getClass(), obj);
            }
        }
    }

    @Override public String toString() {
        return getClass().getSimpleName()+"{drivers=" +
                (hasDrivers() ? Arrays.stream(drivers)
                        .map(RuleDriver::getName)
                        .collect(Collectors.joining(",")) : "none") +
                ",apps=" +
                (hasApps() ? Arrays.stream(apps)
                        .map(AppObjectUpgradeHandler::appString)
                        .collect(Collectors.joining(",")): "none") +
                "}";
    }
}
