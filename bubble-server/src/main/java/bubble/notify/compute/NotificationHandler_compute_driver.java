/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify.compute;

import bubble.cloud.compute.ComputeServiceDriver;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.DelegatedNotificationHandlerBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.notify.NotificationType.compute_driver_response;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public abstract class NotificationHandler_compute_driver<T> extends DelegatedNotificationHandlerBase {

    @Autowired protected BubbleNodeDAO nodeDAO;
    @Autowired protected CloudServiceDAO cloudDAO;

    @Override public void handleNotification(ReceivedNotification n) {

        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("sender not found: "+n.getFromNode());

        final ComputeDriverNotification notification = json(n.getPayloadJson(), ComputeDriverNotification.class);
        final CloudService compute = cloudDAO.findByUuid(notification.getComputeService());

        // sanity check for NPE and infinite loop
        if (compute == null) {
            die("handleNotification: Compute service not found: "+notification.getComputeService());

        } else if (compute.delegated()) {
            die("handleNotification: Compute service is delegated: "+notification.getComputeService());
        }

        try {
            final T result = handle(n, notification, compute.getComputeDriver(configuration));
            notifySender(compute_driver_response, n.getNotificationId(), sender, result);
        } catch (Exception e) {
            log.error("handleNotification: "+e);
            notifySender(compute_driver_response, n.getNotificationId(), sender, e);
        }
    }

    protected abstract T handle(ReceivedNotification n,
                                ComputeDriverNotification notification,
                                ComputeServiceDriver compute) throws Exception;

}
