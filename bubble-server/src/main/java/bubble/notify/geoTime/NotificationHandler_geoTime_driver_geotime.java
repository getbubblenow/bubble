/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify.geoTime;

import bubble.cloud.geoTime.GeoTimeZone;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.DelegatedNotificationHandlerBase;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.notify.NotificationType.geoTime_driver_response;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

public class NotificationHandler_geoTime_driver_geotime extends DelegatedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private CloudServiceDAO cloudDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("sender not found: "+n.getFromNode());

        final GeoTimeNotification geoNotification = json(n.getPayloadJson(), GeoTimeNotification.class);
        final CloudService geoService = cloudDAO.findByAccountAndName(configuration.getThisNode().getAccount(), geoNotification.getGeoTimeService());
        final GeoTimeZone result = geoService.getGeoTimeDriver(configuration).getTimezone(geoNotification.getLat(), geoNotification.getLon());

        notifySender(geoTime_driver_response, n.getNotificationId(), sender, result);
    }

}
