/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.compute;

import bubble.cloud.compute.ComputeNodeSize;
import bubble.cloud.compute.ComputeServiceDriver;
import bubble.model.cloud.notify.ReceivedNotification;

public class NotificationHandler_compute_driver_get_sizes extends NotificationHandler_compute_driver<ComputeNodeSize[]> {

    @Override protected ComputeNodeSize[] handle(ReceivedNotification n,
                                                 ComputeDriverNotification notification,
                                                 ComputeServiceDriver compute) {
        return compute.getSizes().toArray(new ComputeNodeSize[0]);
    }

}
