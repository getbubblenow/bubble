/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.app.passthru;

import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import bubble.model.app.config.AppConfigDriver;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class TlsPassthruAppConfigDriver implements AppConfigDriver {

    @Override public Object getView(Account account, BubbleApp app, String view, Map<String, String> params) {
        // todo
        return null;
    }

    @Override public Object takeAppAction(Account account, BubbleApp app, String view, String action, Map<String, String> params, JsonNode data) {
        // todo
        return null;
    }

    @Override public Object takeItemAction(Account account, BubbleApp app, String view, String action, String id, Map<String, String> params, JsonNode data) {
        // todo
        return null;
    }

}
