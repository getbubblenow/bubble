package bubble.notify;

import bubble.model.cloud.notify.ReceivedNotification;

public interface ReceivedNotificationHandler {
    void handleNotification(ReceivedNotification n);
}
