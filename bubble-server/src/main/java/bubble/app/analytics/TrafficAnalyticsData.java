/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.app.analytics;

import bubble.model.app.AppData;
import bubble.model.device.Device;
import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

public class TrafficAnalyticsData extends AppData {

    public TrafficAnalyticsData(AppData data) {
        copy(this, data);
        this.fqdn = data.getMeta2();
        final Device device = data.getRelated().entity(Device.class);
        if (device != null) setDevice(device.getName());
    }

    @Getter @Setter private String fqdn;

}
