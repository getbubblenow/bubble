/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.main;

import bubble.client.BubbleApiClient;
import org.cobbzilla.util.http.ApiConnectionInfo;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.main.ScriptMainBase;
import org.cobbzilla.wizard.util.RestResponse;

import static bubble.ApiConstants.*;

public class BubbleScriptMainBase<OPT extends BubbleScriptOptions> extends ScriptMainBase<OPT> {

    @Override protected ApiClientBase initApiClient() {
        return new BubbleApiClient(new ApiConnectionInfo(getOptions().getApiBase()));
    }

    @Override protected String getApiHeaderTokenName() { return SESSION_HEADER; }

    @Override protected String getLoginUri(String account) { return AUTH_ENDPOINT + EP_LOGIN; }

    @Override protected String getSessionId(RestResponse response) { return getToken(response.json); }

}
