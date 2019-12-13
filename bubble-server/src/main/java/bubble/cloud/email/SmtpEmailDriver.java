package bubble.cloud.email;

import bubble.cloud.CloudServiceDriverBase;
import bubble.cloud.auth.AuthenticationDriver;
import bubble.cloud.email.mock.MockMailSender;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.mail.SimpleEmailMessage;
import org.cobbzilla.mail.sender.SmtpMailSender;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

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
        try {
            final SimpleEmailMessage email = renderMessage(account, message, contact);
            log.debug("send: sending message "+getSender().getClass().getName()+": "+email);
            getSender().send(email);
        } catch (Exception e) {
            log.error("send failed: "+e);
            return false;
        }
        return true;
    }

    @Override public String getTemplatePath() { return config.getTemplatePath(); }

    public SimpleEmailMessage renderMessage(Account account, AccountMessage message, AccountContact contact) {
        return renderMessage(account, message, contact, configuration, getTemplatePath());
    }

    public static SimpleEmailMessage renderMessage(Account account, AccountMessage message, AccountContact contact,
                                                   BubbleConfiguration configuration, String templatePath) {
        final Map<String, Object> ctx = AuthenticationDriver.buildContext(account, message, contact, configuration);

        final SimpleEmailMessage email = new RenderedEmail(ctx);
        email.setToEmail(contact.getInfo());
        email.setToName(account.getName());
        email.setFromEmail(AuthenticationDriver.render("fromEmail", ctx, message, configuration, templatePath));
        email.setFromName(AuthenticationDriver.render("fromName", ctx, message, configuration, templatePath));
        email.setSubject(AuthenticationDriver.render("subject", ctx, message, configuration, templatePath));
        email.setMessage(AuthenticationDriver.render("message", ctx, message, configuration, templatePath));
        try {
            email.setHtmlMessage(AuthenticationDriver.render("htmlMessage", ctx, message, configuration, templatePath));
        } catch (Exception e) {
            log.debug("Error loading htmlMessage for "+message.templateName("htmlMessage")+": "+e);
        }
        return email;
    }

}
