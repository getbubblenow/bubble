/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.dao;

import bubble.model.account.Account;
import org.cobbzilla.wizard.dao.AbstractSessionDAO;
import org.springframework.stereotype.Repository;

@Repository
public class SessionDAO extends AbstractSessionDAO<Account> {

    @Override protected boolean canStartSession(Account account) { return !account.suspended(); }

}
