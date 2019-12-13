package bubble.model.account.message.handlers;

import bubble.dao.account.AccountPolicyDAO;
import bubble.model.account.AccountContact;
import bubble.model.account.AccountPolicy;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageCompletionHandler;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class AccountVerifyHandler implements AccountMessageCompletionHandler {

    @Autowired private AccountPolicyDAO policyDAO;

    @Override public void confirm(AccountMessage message, NameAndValue[] data) {
        final AccountPolicy policy = policyDAO.findSingleByAccount(message.getAccount());
        log.info("confirm: verifying contact "+message.getContact()+" from account "+message.getAccount());
        policyDAO.update(policy.verifyContact(message.getContact()));
    }

    @Override public void deny(AccountMessage message) {
        final AccountPolicy policy = policyDAO.findSingleByAccount(message.getAccount());
        log.info("deny: removing contact "+message.getContact()+" from account "+message.getAccount());
        policy.removeContact(new AccountContact().setUuid(message.getContact()));
        policyDAO.update(policy);
    }

}
