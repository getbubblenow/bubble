/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.notify.geoLocation;

import bubble.cloud.geoLocation.GeoLocation;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.DelegatedNotificationHandlerBase;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.model.cloud.notify.NotificationType.geoLocation_driver_response;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

public class NotificationHandler_geoLocation_driver_geolocate extends DelegatedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private CloudServiceDAO cloudDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("sender not found: "+n.getFromNode());

        final GeoLocationNotification geoNotification = json(n.getPayloadJson(), GeoLocationNotification.class);
        final CloudService geoService = cloudDAO.findByAccountAndName(configuration.getThisNode().getAccount(), geoNotification.getGeolocationService());
        final GeoLocation location = geoService.getGeoLocateDriver(configuration).geolocate(geoNotification.getIp());

        notifySender(geoLocation_driver_response, n.getNotificationId(), sender, location);
    }

}
