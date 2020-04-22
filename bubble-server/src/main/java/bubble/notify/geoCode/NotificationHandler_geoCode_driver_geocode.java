/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.geoCode;

import bubble.cloud.geoCode.GeoCodeResult;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.DelegatedNotificationHandlerBase;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.notify.NotificationType.geoCode_driver_response;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

public class NotificationHandler_geoCode_driver_geocode extends DelegatedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private CloudServiceDAO cloudDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("sender not found: "+n.getFromNode());

        final GeoCodeNotification geoNotification = json(n.getPayloadJson(), GeoCodeNotification.class);
        final CloudService geoService = cloudDAO.findByAccountAndName(configuration.getThisNode().getAccount(), geoNotification.getGeoCodeService());
        final GeoCodeResult result = geoService.getGeoCodeDriver(configuration).lookup(geoNotification.getLocation());

        notifySender(geoCode_driver_response, n.getNotificationId(), sender, result);
    }

}
