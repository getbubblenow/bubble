package bubble.service.account;

import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import bubble.model.account.AuthenticatorRequest;

public interface AuthenticatorService {
    String authenticate(Account account, AccountPolicy policy, AuthenticatorRequest setToken);
}
