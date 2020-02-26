/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.geoCode.delegate;

import bubble.cloud.DelegatedCloudServiceDriverBase;
import bubble.cloud.geoCode.GeoCodeResult;
import bubble.cloud.geoCode.GeoCodeServiceDriver;
import bubble.cloud.geoLocation.GeoLocation;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.notify.geoCode.GeoCodeNotification;

import static bubble.model.cloud.notify.NotificationType.geoCode_driver_geocode;

public class DelegatedGeoCodeDriver extends DelegatedCloudServiceDriverBase implements GeoCodeServiceDriver {

    public DelegatedGeoCodeDriver(CloudService cloud) { super(cloud); }

    @Override public GeoCodeResult lookup(GeoLocation location) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, geoCode_driver_geocode, new GeoCodeNotification(location, cloud.getName()));
    }

}
