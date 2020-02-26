/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources.notify;

import bubble.dao.cloud.notify.SentNotificationDAO;
import bubble.model.account.Account;
import bubble.model.cloud.notify.SentNotification;

public class SentNotificationsResource extends NotificationsResourceBase<SentNotification, SentNotificationDAO> {

    public SentNotificationsResource(Account account) { super(account); }

}
