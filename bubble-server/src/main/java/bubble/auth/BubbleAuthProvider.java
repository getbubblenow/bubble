/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.auth;

import bubble.dao.SessionDAO;
import bubble.model.account.Account;
import org.cobbzilla.wizard.filters.auth.AuthProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BubbleAuthProvider implements AuthProvider<Account> {

    @Autowired private SessionDAO sessionDAO;

    @Override public Account find(String uuid) { return sessionDAO.find(uuid); }

}