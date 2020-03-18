/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.dao.account;

import bubble.dao.account.message.AccountMessageDAO;
import bubble.model.account.Account;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.model.cloud.BubbleNetwork;
import bubble.service.boot.SelfNodeService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.system.Sleep.sleep;

@AllArgsConstructor @Slf4j
public class AccountInitializer implements Runnable {

    public static final int MAX_ACCOUNT_INIT_RETRIES = 3;
    public static final long COPY_WAIT_TIME = SECONDS.toMillis(2);
    public static final long SEND_MESSAGE_WAIT_TIME = SECONDS.toMillis(1);

    private Account account;
    private AccountDAO accountDAO;
    private AccountMessageDAO messageDAO;
    private SelfNodeService selfNodeService;

    private AtomicBoolean ready = new AtomicBoolean(false);
    public boolean ready() { return ready.get(); }

    private AtomicBoolean canSendAccountMessages = new AtomicBoolean(false);
    public void setCanSendAccountMessages() { canSendAccountMessages.set(true); }

    private AtomicBoolean abort = new AtomicBoolean(false);
    public void setAbort () { abort.set(true); }

    private AtomicBoolean completed = new AtomicBoolean(false);
    public boolean completed () { return completed.get(); }

    private AtomicReference<Exception> error = new AtomicReference<>();
    public Exception getError() { return error.get(); }
    public boolean hasError () { return getError() != null; }

    public AccountInitializer(Account account, AccountDAO accountDAO, AccountMessageDAO messageDAO, SelfNodeService selfNodeService) {
        this.account = account;
        this.accountDAO = accountDAO;
        this.messageDAO = messageDAO;
        this.selfNodeService = selfNodeService;
    }

    @Override public void run() {
        try {
            boolean success = false;
            Exception lastEx = null;
            for (int i=0; i<MAX_ACCOUNT_INIT_RETRIES; i++) {
                try {
                    sleep(COPY_WAIT_TIME, "waiting before copyTemplates");
                    accountDAO.copyTemplates(account, ready);

                    while (!canSendAccountMessages.get() && !abort.get()) {
                        sleep(SEND_MESSAGE_WAIT_TIME, "waiting before sending welcome message");
                    }
                    if (abort.get()) {
                        log.warn("aborting!");
                        return;
                    }

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
            if (account.sendWelcomeEmail()) {
                final BubbleNetwork thisNetwork = selfNodeService.getThisNetwork();
                messageDAO.create(new AccountMessage()
                        .setRemoteHost(account.getRemoteHost())
                        .setAccount(account.getUuid())
                        .setName(account.getUuid())
                        .setNetwork(thisNetwork.getUuid())
                        .setMessageType(AccountMessageType.notice)
                        .setAction(AccountAction.welcome)
                        .setTarget(ActionTarget.account));
            }
        } catch (Exception e) {
            error.set(e);
            // todo: send to errbit
            die("error: "+e, e);
        } finally {
            completed.set(true);
        }
    }
}
