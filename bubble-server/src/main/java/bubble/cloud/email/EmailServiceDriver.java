/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.email;

import bubble.cloud.CloudServiceType;
import bubble.cloud.auth.AuthenticationDriver;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.server.BubbleConfiguration;

import java.util.Map;

public interface EmailServiceDriver extends AuthenticationDriver {

    @Override default CloudServiceType getType() { return CloudServiceType.email; }

    static boolean send(EmailServiceDriver driver, Account account, AccountMessage message, AccountContact contact) {
        final RenderedEmail email = driver.renderMessage(account, message, contact);
        return driver.send(email);
    }

    default RenderedEmail renderMessage(Account account, AccountMessage message, AccountContact contact) {
        return renderMessage(account, message, contact, getConfiguration(), getTemplatePath());
    }

    static RenderedEmail renderMessage(Account account, AccountMessage message, AccountContact contact,
                                       BubbleConfiguration configuration, String templatePath) {
        final Map<String, Object> ctx = AuthenticationDriver.buildContext(account, message, contact, configuration);

        final RenderedEmail email = new RenderedEmail(ctx);
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
