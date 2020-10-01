/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.email.mock;

import bubble.cloud.email.SmtpEmailDriver;
import org.cobbzilla.mail.sender.SmtpMailSender;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class MockEmailDriver extends SmtpEmailDriver {

    private static final MockMailSender MOCK_MAIL_SENDER = new MockMailSender();

    @Override public SmtpMailSender getSender() { return MOCK_MAIL_SENDER; }

    @Override protected boolean isServiceCompatible(final String serviceHost) { return empty(serviceHost); }
}
