/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.cloud.email.mock;

import bubble.cloud.auth.RenderedMessage;
import bubble.cloud.email.RenderedEmail;
import lombok.Getter;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.cobbzilla.mail.SimpleEmailMessage;
import org.cobbzilla.mail.sender.SmtpMailSender;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class MockMailSender extends SmtpMailSender {

    @Getter private static final Map<String, ArrayList<RenderedMessage>> spool = new ConcurrentHashMap<>();

    private static final int MAX_INBOX_SIZE = 20;

    @Override public void send(SimpleEmailMessage email) throws EmailException {
        if (!(email instanceof RenderedEmail)) email = new RenderedEmail(email);
        synchronized (spool) {
            final ArrayList<RenderedMessage> inbox = spool.computeIfAbsent(email.getToEmail(), m -> new ArrayList<>());
            inbox.add(0, (RenderedEmail) email);

            // remove old messages, keep inbox size from growing infinitely
            while (inbox.size() > MAX_INBOX_SIZE) inbox.remove(inbox.size()-1);
        }
    }

    @Override protected void sendEmail_internal(Email email) throws EmailException {
        die("sendEmail_internal: unsupported");
    }
}
