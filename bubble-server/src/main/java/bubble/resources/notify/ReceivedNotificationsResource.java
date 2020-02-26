/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources.notify;

import bubble.dao.cloud.notify.ReceivedNotificationDAO;
import bubble.model.account.Account;
import bubble.model.cloud.notify.ReceivedNotification;

public class ReceivedNotificationsResource extends NotificationsResourceBase<ReceivedNotification, ReceivedNotificationDAO> {

    public ReceivedNotificationsResource(Account account) { super(account); }

}
