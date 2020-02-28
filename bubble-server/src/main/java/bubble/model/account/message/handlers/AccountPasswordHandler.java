/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.model.account.message.handlers;

import bubble.dao.account.AccountDAO;
import bubble.model.account.Account;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageCompletionHandler;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.model.HashedPassword;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class AccountPasswordHandler implements AccountMessageCompletionHandler {

    @Autowired private AccountDAO accountDAO;

    @Override public boolean validate(AccountMessage message, NameAndValue[] data) {
        final String password = NameAndValue.find(data, "password");
        final ConstraintViolationBean violation = Account.validatePassword(password);
        if (violation != null) throw new SimpleViolationException(violation);
        return true;
    }

    @Override public void confirm(AccountMessage message, NameAndValue[] data) {
        final Account account = accountDAO.findByUuid(message.getAccount());
        account.setHashedPassword(new HashedPassword(NameAndValue.find(data, "password")));
        accountDAO.update(account);
    }

}
