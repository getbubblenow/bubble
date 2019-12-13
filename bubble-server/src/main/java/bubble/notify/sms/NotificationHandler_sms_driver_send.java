package bubble.notify.sms;

import bubble.model.cloud.notify.NotificationType;
import bubble.notify.auth.DelegatedAuthNotificationHandlerBase;

import static bubble.model.cloud.notify.NotificationType.sms_driver_response;

public class NotificationHandler_sms_driver_send extends DelegatedAuthNotificationHandlerBase {

    @Override protected NotificationType getResponseType() { return sms_driver_response; }

}
