/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.compute;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.cloud.compute.OsImage;
import bubble.model.cloud.notify.ReceivedNotification;

public class NotificationHandler_compute_driver_get_os extends NotificationHandler_compute_driver<OsImage> {

    @Override protected OsImage handle(ReceivedNotification n,
                                                 ComputeDriverNotification notification,
                                                 ComputeServiceDriver compute) {
        return compute.getOs();
    }

}
