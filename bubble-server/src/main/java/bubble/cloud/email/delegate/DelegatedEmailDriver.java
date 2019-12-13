package bubble.cloud.email.delegate;

import bubble.cloud.auth.DelegatedAuthDriverBase;
import bubble.cloud.email.EmailServiceDriver;
import bubble.cloud.email.mock.MockMailSender;
import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.model.cloud.BubbleNode;
import bubble.model.cloud.CloudService;
import bubble.notify.email.EmailDriverNotification;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.mail.EmailException;
import org.springframework.beans.factory.annotation.Autowired;

import static bubble.cloud.email.EmailDriverConfig.DEFAULT_TEMPLATE_PATH;
import static bubble.cloud.email.SmtpEmailDriver.renderMessage;
import static bubble.model.cloud.notify.NotificationType.email_driver_send;
import static org.cobbzilla.mail.sender.SmtpMailSender.isTestDomain;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Slf4j
public class DelegatedEmailDriver extends DelegatedAuthDriverBase implements EmailServiceDriver {

    public DelegatedEmailDriver(CloudService cloud) { super(cloud); }

    private final MockMailSender mockMailSender = new MockMailSender();

    @Getter @Autowired private BubbleConfiguration configuration;

    @Override public boolean send(Account account, AccountMessage message, AccountContact contact) {
        if (isTestDomain(contact.getInfo())) return sendToMock(account, message, contact);
        final BubbleNode delegate = getDelegateNode();
        return notificationService.notifySync(delegate, email_driver_send, notification(new EmailDriverNotification()
                .setAccount(account)
                .setMessage(message)
                .setContact(contact)));
    }

    public boolean sendToMock(Account account, AccountMessage message, AccountContact contact) {
        try {
            log.info("send: sending to MockMailSender: "+message);
            mockMailSender.send(renderMessage(account, message, contact, getConfiguration(), DEFAULT_TEMPLATE_PATH));
        } catch (EmailException e) {
            return die("send: "+e, e);
        }
        return true;
    }

}
