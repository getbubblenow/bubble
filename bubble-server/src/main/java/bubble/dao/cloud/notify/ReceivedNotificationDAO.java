/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.dao.cloud.notify;

import bubble.model.cloud.notify.ReceivedNotification;
import org.springframework.stereotype.Repository;

import java.util.List;

import static bubble.model.cloud.notify.NotificationProcessingStatus.received;

@Repository
public class ReceivedNotificationDAO extends NotificationBaseDAO<ReceivedNotification> {

    public List<ReceivedNotification> findNewReceived() {
        return findByField("processingStatus", received);
    }

}
