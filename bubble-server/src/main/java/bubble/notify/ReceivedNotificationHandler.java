/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify;

import bubble.model.cloud.notify.ReceivedNotification;

public interface ReceivedNotificationHandler {
    void handleNotification(ReceivedNotification n);
}
