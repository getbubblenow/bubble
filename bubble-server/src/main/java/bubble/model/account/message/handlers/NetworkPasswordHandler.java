package bubble.model.account.message.handlers;

import bubble.model.account.message.AccountMessage;
import bubble.model.account.message.AccountMessageCompletionHandler;
import bubble.service.backup.NetworkKeysService;
import org.cobbzilla.util.collection.NameAndValue;
import org.springframework.beans.factory.annotation.Autowired;

public class NetworkPasswordHandler implements AccountMessageCompletionHandler {

    @Autowired private NetworkKeysService keysService;

    @Override public void confirm(AccountMessage message, NameAndValue[] data) {
        keysService.registerView(message.getRequestId());
    }

}
