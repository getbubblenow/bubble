/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.dao.cloud.notify;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.cloud.notify.NotificationBase;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotificationBaseDAO<E extends NotificationBase> extends AccountOwnedEntityDAO<E> {}
