/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao;

import bubble.dao.account.AccountDAO;
import bubble.model.account.Account;
import org.cobbzilla.wizard.dao.AbstractSessionDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class SessionDAO extends AbstractSessionDAO<Account> {

    @Autowired private AccountDAO accountDAO;

    @Override protected boolean canStartSession(Account account) { return !account.suspended(); }

    @Override public String create(Account account) {
        account.setFirstAdmin(account.getUuid().equals(accountDAO.getFirstAdmin().getUuid()));
        return super.create(account);
    }

}
