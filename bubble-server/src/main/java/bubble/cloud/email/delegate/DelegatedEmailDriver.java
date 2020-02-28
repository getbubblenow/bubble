/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.email.delegate;

import bubble.cloud.auth.DelegatedAuthDriverBase;
import bubble.cloud.auth.RenderedMessage;
import bubble.cloud.email.EmailDriverConfig;
import bubble.cloud.email.EmailServiceDriver;
import bubble.cloud.email.RenderedEmail;
import bubble.cloud.email.mock.MockMailSender;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.notify.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.mail.EmailException;

import static bubble.cloud.email.EmailDriverConfig.DEFAULT_TEMPLATE_PATH;
import static bubble.model.cloud.notify.NotificationType.email_driver_send;
import static org.cobbzilla.mail.sender.SmtpMailSender.isTestDomain;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Slf4j
public class DelegatedEmailDriver extends DelegatedAuthDriverBase implements EmailServiceDriver {

    public DelegatedEmailDriver(CloudService cloud) { super(cloud); }

    private final MockMailSender mockMailSender = new MockMailSender();

    @Override public boolean send(Account account, AccountMessage message, AccountContact contact) {
        if (isTestDomain(contact.getInfo())) return sendToMock(account, message, contact);
        return EmailServiceDriver.send(this, account, message, contact);
    }

    public boolean sendToMock(Account account, AccountMessage message, AccountContact contact) {
        try {
            log.info("send: sending to MockMailSender: "+message);
            mockMailSender.send(EmailServiceDriver.renderMessage(account, message, contact, getConfiguration(), DEFAULT_TEMPLATE_PATH));
        } catch (EmailException e) {
            return die("send: "+e, e);
        }
        return true;
    }

    @Override protected String getDefaultTemplatePath() { return EmailDriverConfig.DEFAULT_TEMPLATE_PATH; }

    @Override public NotificationType getSendNotificationType() { return email_driver_send; }

    @Override protected Class<? extends RenderedMessage> getRenderedMessageClass() { return RenderedEmail.class; }

}
