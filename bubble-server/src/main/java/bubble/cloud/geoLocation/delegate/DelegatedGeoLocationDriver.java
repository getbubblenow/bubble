/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.geoLocation.delegate;

import bubble.cloud.DelegatedCloudServiceDriverBase;
import bubble.cloud.geoLocation.GeoLocateServiceDriver;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.notify.geoLocation.GeoLocationNotification;

import static bubble.model.cloud.notify.NotificationType.geoLocation_driver_geolocate;

public class DelegatedGeoLocationDriver extends DelegatedCloudServiceDriverBase implements GeoLocateServiceDriver {

    public DelegatedGeoLocationDriver(CloudService cloud) { super(cloud); }

    @Override public GeoLocation geolocate(String ip) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, geoLocation_driver_geolocate, new GeoLocationNotification(ip, cloud.getName()));
    }
}
