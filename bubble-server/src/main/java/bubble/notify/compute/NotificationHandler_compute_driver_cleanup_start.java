package bubble.notify.compute;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.ReceivedNotification;

public class NotificationHandler_compute_driver_cleanup_start extends NotificationHandler_compute_driver<BubbleNode> {

    @Override protected BubbleNode handle(ReceivedNotification n,
                                          ComputeDriverNotification notification,
                                          ComputeServiceDriver compute) throws Exception {
        return compute.cleanupStart(notification.getNode());
    }

}
