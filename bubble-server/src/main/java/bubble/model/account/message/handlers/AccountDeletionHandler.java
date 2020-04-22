/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account.message.handlers;

import bubble.dao.SessionDAO;
import bubble.dao.account.AccountDAO;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageCompletionHandler;
import org.cobbzilla.util.collection.NameAndValue;
import org.springframework.beans.factory.annotation.Autowired;

public class AccountDeletionHandler implements AccountMessageCompletionHandler {

    @Autowired private AccountDAO accountDAO;
    @Autowired private SessionDAO sessionDAO;

    @Override public void confirm(AccountMessage message, NameAndValue[] data) {
        accountDAO.delete(message.getAccount());
        sessionDAO.invalidateAllSessions(message.getAccount());
    }

}
