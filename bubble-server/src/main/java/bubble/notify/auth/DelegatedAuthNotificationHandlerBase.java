package bubble.notify.auth;

import bubble.cloud.auth.AuthenticationDriver;
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

public abstract class DelegatedAuthNotificationHandlerBase extends DelegatedNotificationHandlerBase {

    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private CloudServiceDAO cloudDAO;

    @Override public void handleNotification(ReceivedNotification n) {
        final BubbleNode sender = nodeDAO.findByUuid(n.getFromNode());
        if (sender == null) die("sender not found: "+n.getFromNode());

        final AuthenticatorDriverNotification notification = json(n.getPayloadJson(), AuthenticatorDriverNotification.class);
        final CloudService authService = cloudDAO.findByUuid(notification.getAuthService());

        final AuthenticationDriver driver = authService.getAuthenticationDriver(configuration);
        final boolean sent = driver.send(notification.getAccount(), notification.getMessage(), notification.getContact());

        notifySender(getResponseType(), n.getNotificationId(), sender, sent);
    }

    protected abstract NotificationType getResponseType();

}
