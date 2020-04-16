/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.app;

import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.app.AppSite;
import bubble.model.app.BubbleApp;

public class AppSiteDataResource extends DataResourceBase {

    public AppSiteDataResource(Account account, BubbleApp app, AppSite site) {
        super(account, app, new AppData()
                .setApp(app.getUuid())
                .setSite(site.getUuid())
                .setAccount(account == null ? null : account.getUuid()));
    }

}
