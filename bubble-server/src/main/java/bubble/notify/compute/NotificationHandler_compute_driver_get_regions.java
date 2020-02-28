/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.compute;

import bubble.cloud.CloudRegion;
import bubble.cloud.compute.ComputeServiceDriver;
import bubble.model.cloud.notify.ReceivedNotification;

public class NotificationHandler_compute_driver_get_regions extends NotificationHandler_compute_driver<CloudRegion[]> {

    @Override protected CloudRegion[] handle(ReceivedNotification n,
                                             ComputeDriverNotification notification,
                                             ComputeServiceDriver compute) {
        return compute.getRegions().toArray(new CloudRegion[0]);
    }

}
