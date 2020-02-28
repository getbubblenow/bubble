/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.resources.notify;

import bubble.dao.cloud.notify.ReceivedNotificationDAO;
import bubble.model.account.Account;
import bubble.model.cloud.notify.ReceivedNotification;

public class ReceivedNotificationsResource extends NotificationsResourceBase<ReceivedNotification, ReceivedNotificationDAO> {

    public ReceivedNotificationsResource(Account account) { super(account); }

}
