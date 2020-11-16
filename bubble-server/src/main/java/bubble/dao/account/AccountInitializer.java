/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.account;

import bubble.dao.account.message.AccountMessageDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.AccountPolicy;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.model.cloud.BubbleNetwork;
import bubble.server.BubbleConfiguration;
import bubble.service.boot.SelfNodeService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class AccountInitializer implements Runnable {

    public static final int MAX_ACCOUNT_INIT_RETRIES = 3;
    public static final long COPY_WAIT_TIME = SECONDS.toMillis(2);
    public static final long SEND_MESSAGE_WAIT_TIME = SECONDS.toMillis(1);

    private final Account account;
    private final AccountDAO accountDAO;
    private final AccountPolicyDAO policyDAO;
    private final AccountMessageDAO messageDAO;
    private final SelfNodeService selfNodeService;
    private final BubbleConfiguration configuration;

    private final AtomicBoolean ready = new AtomicBoolean(false);
    public boolean ready() { return ready.get(); }

    private final AtomicBoolean canSendAccountMessages = new AtomicBoolean(false);
    public void setCanSendAccountMessages() { canSendAccountMessages.set(true); }

    private final AtomicBoolean abort = new AtomicBoolean(false);
    public void setAbort () { abort.set(true); }

    private final AtomicBoolean completed = new AtomicBoolean(false);
    public boolean completed () { return completed.get(); }

    private final AtomicReference<Exception> error = new AtomicReference<>();
    public Exception getError() { return error.get(); }
    public boolean hasError () { return getError() != null; }

    public AccountInitializer(Account account,
                              AccountDAO accountDAO,
                              AccountPolicyDAO policyDAO,
                              AccountMessageDAO messageDAO,
                              SelfNodeService selfNodeService,
                              BubbleConfiguration configuration) {
        this.account = account;
        this.accountDAO = accountDAO;
        this.policyDAO = policyDAO;
        this.messageDAO = messageDAO;
        this.selfNodeService = selfNodeService;
        this.configuration = configuration;
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
                    success = true;
                    break;
                } catch (Exception e) {
                    lastEx = e;
                    log.error("copyTemplates error: " + e);
                }
            }
            if (!success) throw lastEx;

            if (selfNodeService.getThisNetwork().local() && !configuration.testMode()) {
                // running locally, initial contact is always validated
                final String accountUuid = account.getUuid();
                final AccountPolicy policy = policyDAO.findSingleByAccount(accountUuid);
                final AccountContact contact = policy != null && policy.hasAccountContacts() ? policy.getAccountContacts()[0] : null;
                if (contact == null) {
                    die("no contact found for welcome message: account="+accountUuid);
                } else {
                    policyDAO.update(policy.verifyContact(contact.getUuid()));
                }

            } else if (account.sendWelcomeEmail()) {
                final BubbleNetwork thisNetwork = selfNodeService.getThisNetwork();
                final String accountUuid = account.getUuid();
                final AccountPolicy policy = policyDAO.findSingleByAccount(accountUuid);
                final String contact = policy != null && policy.hasAccountContacts() ? policy.getAccountContacts()[0].getUuid() : null;
                if (contact == null) die("no contact found for welcome message: account="+accountUuid);
                messageDAO.create(new AccountMessage()
                        .setRemoteHost(account.getRemoteHost())
                        .setAccount(accountUuid)
                        .setName(accountUuid)
                        .setNetwork(thisNetwork.getUuid())
                        .setMessageType(AccountMessageType.request)
                        .setAction(AccountAction.welcome)
                        .setTarget(ActionTarget.account)
                        .setContact(contact));
            }
        } catch (Exception e) {
            error.set(e);
            die("error: "+e, e);
        } finally {
            completed.set(true);
        }
    }
}
