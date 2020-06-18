/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account;

import org.cobbzilla.wizard.auth.LoginRequest;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class AccountLoginRequest extends LoginRequest {

    public String getEmail () { return getName(); }

    public AccountLoginRequest setEmail(String email) {
        setName(email);
        return this;
    }

    public boolean hasEmail () { return !empty(getEmail()); }

}
