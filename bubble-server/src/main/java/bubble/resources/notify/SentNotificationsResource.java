package bubble.resources.notify;

import bubble.dao.cloud.notify.SentNotificationDAO;
import bubble.model.account.Account;
import bubble.model.cloud.notify.SentNotification;

public class SentNotificationsResource extends NotificationsResourceBase<SentNotification, SentNotificationDAO> {

    public SentNotificationsResource(Account account) { super(account); }

}
