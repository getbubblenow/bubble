package bubble.cloud.geoTime.delegate;

import bubble.cloud.DelegatedCloudServiceDriverBase;
import bubble.cloud.geoTime.GeoTimeServiceDriver;
import bubble.cloud.geoTime.GeoTimeZone;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.notify.geoTime.GeoTimeNotification;

import static bubble.model.cloud.notify.NotificationType.geoTime_driver_geotime;

public class DelegatedGeoTimeDriver extends DelegatedCloudServiceDriverBase implements GeoTimeServiceDriver {

    public DelegatedGeoTimeDriver(CloudService cloud) { super(cloud); }

    @Override public GeoTimeZone getTimezone(String lat, String lon) {
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, geoTime_driver_geotime, new GeoTimeNotification(lat, lon, cloud.getName()));
    }

}
