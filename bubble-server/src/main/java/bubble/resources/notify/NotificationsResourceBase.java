/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources.notify;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.account.Account;
import bubble.model.cloud.notify.NotificationBase;
import bubble.resources.account.AccountOwnedResource;

public class NotificationsResourceBase<E extends NotificationBase, DAO extends AccountOwnedEntityDAO<E>>
        extends AccountOwnedResource<E, DAO> {

    public NotificationsResourceBase(Account account) { super(account); }

}
