/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.device;

import java.util.Comparator;

public class DeviceStatusFirstComparator implements Comparator<Device> {

    public static DeviceStatusFirstComparator DEVICE_WITH_STATUS_FIRST = new DeviceStatusFirstComparator();

    @Override public int compare(Device d1, Device d2) {
        if (d1.hasStatus() && d2.hasStatus()) {
            return Long.compare(d2.getCtime(), d1.getCtime());
        }
        if (d1.hasStatus()) return -1;
        if (d2.hasStatus()) return 1;
        return Long.compare(d2.getCtime(), d1.getCtime());
    }

}
