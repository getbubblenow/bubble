/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.email;

import bubble.cloud.CloudServiceDriverBase;
import bubble.cloud.auth.RenderedMessage;
import bubble.cloud.email.mock.MockMailSender;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.mail.sender.SmtpMailSender;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class SmtpEmailDriver extends CloudServiceDriverBase<EmailDriverConfig> implements EmailServiceDriver {

    private static final String PARAM_USER = "user";
    private static final String PARAM_PASSWORD = "password";
    private static final String PARAM_HOST = "host";
    private static final String PARAM_PORT = "port";

    @Autowired @Getter protected BubbleConfiguration configuration;

    @Getter(lazy=true) private final SmtpMailSender sender = initSender();
    private SmtpMailSender initSender() {
        final EmailDriverConfig cfg = (EmailDriverConfig) new EmailDriverConfig(this.config)
                .setUser(credentials.getParam(PARAM_USER))
                .setPassword(credentials.getParam(PARAM_PASSWORD))
                .setHost(credentials.getParam(PARAM_HOST))
                .setPort(credentials.getIntParam(PARAM_PORT));
        final SmtpMailSender smtpSender = new SmtpMailSender(cfg);
        smtpSender.setDebugSender(new MockMailSender());
        return smtpSender;
    }

    @Override public boolean send(Account account, AccountMessage message, AccountContact contact) {
        return EmailServiceDriver.send(this, account, message, contact);
    }

    @Override public boolean send(RenderedMessage email) {
        try {
            getSender().send((RenderedEmail) email);
            return true;
        } catch (Exception e) {
            log.error("send failed: "+e);
            return false;
        }
    }

    @Override public String getTemplatePath() { return config.getTemplatePath(); }

}
