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
