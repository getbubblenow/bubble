/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.email;

import bubble.cloud.CloudServiceDriverBase;
import bubble.cloud.auth.RenderedMessage;
import bubble.cloud.email.mock.MockMailSender;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.model.cloud.CloudCredentials;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.mail.sender.SmtpMailSender;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Slf4j
public class SmtpEmailDriver extends CloudServiceDriverBase<EmailDriverConfig> implements EmailServiceDriver {
    protected static final String SENDGRID_SMTP = "smtp.sendgrid.net";

    private static final List<String> SEPARATE_DRIVERS_SMTPS = new ArrayList<>();

    protected static final String PARAM_USER = "user";
    protected static final String PARAM_PASSWORD = "password";
    protected static final String PARAM_HOST = "host";
    protected static final String PARAM_PORT = "port";

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

    @Override public void setCredentials(CloudCredentials credentials) {
        super.setCredentials(credentials);
        if (credentials != null && !isServiceCompatible(credentials.getParam(PARAM_HOST))) {
            die("Specified Smtp Email Driver is not compatible with given config: " + this.getClass().getSimpleName());
        }
    }

    protected boolean isServiceCompatible(final String serviceHost) {
        // Allow even Sendgrid here if Subusers are not supported for specified API key
        return !SEPARATE_DRIVERS_SMTPS.contains(serviceHost);
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
