package bubble.cloud.email.mock;

import bubble.cloud.email.SmtpEmailDriver;
import org.cobbzilla.mail.sender.SmtpMailSender;

public class MockEmailDriver extends SmtpEmailDriver {

    private static final MockMailSender MOCK_MAIL_SENDER = new MockMailSender();

    @Override public SmtpMailSender getSender() { return MOCK_MAIL_SENDER; }

}
