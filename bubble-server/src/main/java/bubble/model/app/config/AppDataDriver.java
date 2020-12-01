/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.app.config;

import bubble.model.account.Account;
import bubble.model.app.AppSite;
import bubble.model.app.BubbleApp;
import bubble.model.device.Device;
import org.cobbzilla.wizard.model.search.SearchResults;
import org.cobbzilla.wizard.model.search.SearchQuery;

import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

public interface AppDataDriver {

    SearchResults query(Account caller, Device device, BubbleApp app, AppSite site, AppDataView view, SearchQuery query);

    default void takeAction(String id, String action) {
        throw invalidEx("err.data.action.notSupported", "Action is not supported", action);
    }

}
