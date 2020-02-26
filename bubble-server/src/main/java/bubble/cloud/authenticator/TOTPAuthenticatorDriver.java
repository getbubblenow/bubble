/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.cloud.authenticator;

import bubble.dao.account.AccountPolicyDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.service.account.StandardAccountMessageService;
import org.springframework.beans.factory.annotation.Autowired;

public class TOTPAuthenticatorDriver implements AuthenticatorServiceDriver {

    @Override public boolean disableDelegation() { return true; }

    @Autowired private AccountPolicyDAO policyDAO;
    @Autowired private StandardAccountMessageService messageService;

    @Override public boolean send(Account account, AccountMessage message, AccountContact contact) {
        messageService.confirmationToken(policyDAO.findSingleByAccount(account.getUuid()), message, contact);
        return true;
    }

}
