/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.notify.auth;

import bubble.cloud.auth.AuthenticationDriver;
import bubble.cloud.auth.RenderedMessage;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.NotificationType;
import bubble.model.cloud.notify.ReceivedNotification;
import bubble.notify.DelegatedNotificationHandlerBase;
import bubble.notify.authenticator.AuthenticatorDriverNotification;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;

public abstract class DelegatedAuthNotificationHandlerBase extends DelegatedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private CloudServiceDAO cloudDAO;

    protected abstract NotificationType getResponseType();

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("sender not found: "+n.getFromNode());

        final AuthenticatorDriverNotification notification = json(n.getPayloadJson(), AuthenticatorDriverNotification.class);
        final CloudService authService = cloudDAO.findByUuid(notification.getAuthService());

        final AuthenticationDriver driver = authService.getAuthenticationDriver(configuration);

        final Class<? extends RenderedMessage> renderedMessageClass = forName(notification.getRenderedMessageClass());
        final RenderedMessage renderedMessage = json(notification.getRenderedMessage(), renderedMessageClass);

        final boolean sent = driver.send(renderedMessage);

        notifySender(getResponseType(), n.getNotificationId(), sender, sent);
    }

}
