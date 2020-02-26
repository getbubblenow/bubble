/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify;

import bubble.model.cloud.notify.ReceivedNotification;

public interface ReceivedNotificationHandler {
    void handleNotification(ReceivedNotification n);
}
