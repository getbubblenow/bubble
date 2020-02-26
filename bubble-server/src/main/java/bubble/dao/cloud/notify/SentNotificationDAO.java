/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.dao.cloud.notify;

import bubble.model.cloud.notify.SentNotification;
import org.springframework.stereotype.Repository;

@Repository
public class SentNotificationDAO extends NotificationBaseDAO<SentNotification> {}
