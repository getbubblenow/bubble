package bubble.resources.notify;

import bubble.dao.cloud.notify.ReceivedNotificationDAO;
import bubble.model.account.Account;
import bubble.model.cloud.notify.ReceivedNotification;

public class ReceivedNotificationsResource extends NotificationsResourceBase<ReceivedNotification, ReceivedNotificationDAO> {

    public ReceivedNotificationsResource(Account account) { super(account); }

}
