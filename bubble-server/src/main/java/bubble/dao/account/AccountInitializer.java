package bubble.dao.account;

import bubble.dao.account.message.AccountMessageDAO;
import bubble.model.account.Account;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.system.Sleep.sleep;

@AllArgsConstructor @Slf4j
public class AccountInitializer implements Runnable {

    public static final int MAX_ACCOUNT_INIT_RETRIES = 3;
    public static final long COPY_WAIT_TIME = SECONDS.toMillis(3);

    private AccountDAO accountDAO;
    private Account account;
    private AccountMessageDAO messageDAO;

    @Override public void run() {
        try {
            boolean success = false;
            Exception lastEx = null;
            for (int i=0; i<MAX_ACCOUNT_INIT_RETRIES; i++) {
                try {
                    sleep(COPY_WAIT_TIME, "waiting before copyTemplates");
                    accountDAO.copyTemplates(account);

                    if (account.hasPolicy() && account.getPolicy().hasAccountContacts()) {
                        messageDAO.sendVerifyRequest(account.getRemoteHost(), account, account.getPolicy().getAccountContacts()[0]);
                    }
                    success = true;
                    break;
                } catch (Exception e) {
                    lastEx = e;
                    log.error("copyTemplates error: " + e);
                }
            }
            if (!success) throw lastEx;
            messageDAO.create(new AccountMessage()
                    .setRemoteHost(account.getRemoteHost())
                    .setAccount(account.getUuid())
                    .setName(account.getUuid())
                    .setMessageType(AccountMessageType.notice)
                    .setAction(AccountAction.welcome)
                    .setTarget(ActionTarget.account));
        } catch (Exception e) {
            // todo: send to errbit
            die("error: "+e, e);
        }
    }
}
