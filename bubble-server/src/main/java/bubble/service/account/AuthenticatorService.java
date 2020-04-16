/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.account;

import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import bubble.model.account.AuthenticatorRequest;

public interface AuthenticatorService {
    String authenticate(Account account, AccountPolicy policy, AuthenticatorRequest setToken);
}
