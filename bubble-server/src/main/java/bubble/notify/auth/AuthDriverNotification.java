package bubble.notify.auth;

import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.notify.SynchronousNotification;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain=true)
public class AuthDriverNotification extends SynchronousNotification {

    @Getter @Setter private String authService;
    @Getter @Setter private Account account;
    @Getter @Setter private AccountMessage message;
    @Getter @Setter private AccountContact contact;

}
