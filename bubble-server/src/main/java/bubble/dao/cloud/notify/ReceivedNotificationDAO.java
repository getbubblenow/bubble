package bubble.dao.cloud.notify;

import bubble.model.cloud.notify.ReceivedNotification;
import org.springframework.stereotype.Repository;

import java.util.List;

import static bubble.model.cloud.notify.NotificationProcessingStatus.received;

@Repository
public class ReceivedNotificationDAO extends NotificationBaseDAO<ReceivedNotification> {

    public List<ReceivedNotification> findNewReceived() {
        return findByField("processingStatus", received);
    }

}
