package bubble.app.analytics;

import bubble.model.app.AppData;
import bubble.model.device.Device;
import lombok.Getter;
import lombok.Setter;

import static bubble.rule.analytics.TrafficAnalytics.FQDN_SEP;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

public class TrafficAnalyticsData extends AppData {

    public TrafficAnalyticsData(AppData data) {
        copy(this, data);
        final String[] parts = getKey().split(FQDN_SEP);
        this.fqdn = parts[0];
        this.timeInterval = parts[1];
        final Device device = data.getRelated().entity(Device.class);
        if (device != null) setDevice(device.getName());
    }

    @Getter @Setter private String fqdn;
    @Getter @Setter private String timeInterval;
}
