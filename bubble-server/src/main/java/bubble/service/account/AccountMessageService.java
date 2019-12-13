package bubble.service.account;

import bubble.model.account.message.AccountMessage;

public interface AccountMessageService {

    boolean send(AccountMessage message);

}
