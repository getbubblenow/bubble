package bubble.notify.email;

import bubble.model.cloud.notify.NotificationType;
import bubble.notify.auth.DelegatedAuthNotificationHandlerBase;

import static bubble.model.cloud.notify.NotificationType.email_driver_response;

public class NotificationHandler_email_driver_send extends DelegatedAuthNotificationHandlerBase {

    @Override protected NotificationType getResponseType() { return email_driver_response; }

}
