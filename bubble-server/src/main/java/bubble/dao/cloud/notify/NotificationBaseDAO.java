package bubble.dao.cloud.notify;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.cloud.notify.NotificationBase;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotificationBaseDAO<E extends NotificationBase> extends AccountOwnedEntityDAO<E> {}
