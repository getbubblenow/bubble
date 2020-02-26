/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.main;

import org.cobbzilla.wizard.auth.LoginRequest;
import org.cobbzilla.wizard.main.MainApiBase;
import org.cobbzilla.wizard.util.RestResponse;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

public abstract class BubbleApiMain<OPT extends BubbleApiOptionsBase> extends MainApiBase<OPT> {

    @Override protected Object buildLoginRequest(OPT options) {
        return new LoginRequest(options.getAccount(), options.getPassword());
    }

    @Override protected String getApiHeaderTokenName() { return SESSION_HEADER; }

    @Override protected String getLoginUri(String account) { return AUTH_ENDPOINT + EP_LOGIN; }

    @Override protected String getSessionId(RestResponse response) { return getToken(response.json); }

    @Override protected void setSecondFactor(Object loginRequest, String token) {
        notSupported("setSecondFactor");
    }

}
