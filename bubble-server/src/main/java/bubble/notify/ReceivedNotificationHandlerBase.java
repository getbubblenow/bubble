package bubble.notify;

import bubble.model.cloud.notify.ReceivedNotification;
import bubble.server.BubbleConfiguration;
import bubble.service.notify.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class ReceivedNotificationHandlerBase implements ReceivedNotificationHandler {

    @Autowired protected BubbleConfiguration configuration;
    @Autowired protected NotificationService notificationService;

    @Override public void handleNotification(ReceivedNotification n) {
        log.debug(getClass().getSimpleName()+" received notification: "+json(n));
    }

}
