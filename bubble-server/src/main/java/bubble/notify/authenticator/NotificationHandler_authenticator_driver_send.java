/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.authenticator;

import bubble.model.cloud.notify.NotificationType;
import bubble.notify.auth.DelegatedAuthNotificationHandlerBase;

import static bubble.model.cloud.notify.NotificationType.authenticator_driver_response;

public class NotificationHandler_authenticator_driver_send extends DelegatedAuthNotificationHandlerBase {

    @Override protected NotificationType getResponseType() { return authenticator_driver_response; }

}
