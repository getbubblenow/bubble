package bubble.notify.compute;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.service.cloud.NodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class NotificationHandler_compute_driver_stop extends NotificationHandler_compute_driver<BubbleNode> {

    @Autowired private NodeService nodeService;

    @Override protected BubbleNode handle(ReceivedNotification n,
                                          ComputeDriverNotification notification,
                                          ComputeServiceDriver compute) throws Exception {
        return nodeService.stopNode(compute, notification.getNode());
    }

}
