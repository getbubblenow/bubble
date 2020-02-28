/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service_dbfilter;

import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import bubble.model.account.AuthenticatorRequest;
import bubble.service.account.AuthenticatorService;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Service
public class DbFilterAuthenticatorService implements AuthenticatorService {

    @Override public String authenticate(Account account, AccountPolicy policy, AuthenticatorRequest setToken) {
        return notSupported("authenticate");
    }

}
