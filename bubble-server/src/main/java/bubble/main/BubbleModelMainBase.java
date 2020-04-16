/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.main;

import bubble.client.BubbleApiClient;
import org.cobbzilla.util.http.ApiConnectionInfo;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.main.ModelSetupMainBase;
import org.cobbzilla.wizard.util.RestResponse;

import static bubble.ApiConstants.*;

public abstract class BubbleModelMainBase<OPT extends BubbleModelOptions> extends ModelSetupMainBase<OPT> {

    @Override protected ApiClientBase initApiClient() {
        return new BubbleApiClient(new ApiConnectionInfo(getOptions().getApiBase()));
    }

    @Override protected String getApiHeaderTokenName() { return SESSION_HEADER; }

    @Override protected String getLoginUri(String account) { return AUTH_ENDPOINT + EP_LOGIN; }

    @Override protected String getSessionId(RestResponse response) { return getToken(response.json); }

}
