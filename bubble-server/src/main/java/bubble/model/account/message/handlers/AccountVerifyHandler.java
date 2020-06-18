/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account.message.handlers;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.AccountPolicy;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageCompletionHandler;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class AccountVerifyHandler implements AccountMessageCompletionHandler {

    @Autowired private AccountDAO accountDAO;
    @Autowired private AccountPolicyDAO policyDAO;

    @Override public void confirm(AccountMessage message, NameAndValue[] data) {
        final AccountPolicy policy = policyDAO.findSingleByAccount(message.getAccount());
        final String contact = message.getRequest().getContact();
        log.info("confirm: verifying contact "+ contact +" from account "+message.getAccount());
        policyDAO.update(policy.verifyContact(contact));
    }

    @Override public void deny(AccountMessage message) {
        final Account account = accountDAO.findByUuid(message.getAccount());
        if (account == null) {
            log.warn("deny: account not found: "+message.getAccount());
            return;
        }
        final AccountPolicy policy = policyDAO.findSingleByAccount(message.getAccount());
        final AccountContact contact = policy.findContact(new AccountContact().setUuid(message.getRequest().getContact()));
        if (contact == null) {
            log.warn("deny: contact not found in policy: "+contact);
            return;
        }
        log.info("deny: removing contact "+ contact +" from account "+message.getAccount());
        policy.removeContact(account, contact);
        policyDAO.update(policy);
    }

}
