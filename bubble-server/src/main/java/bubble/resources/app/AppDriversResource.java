/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources.app;

import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import bubble.resources.driver.DriversResourceBase;

public class AppDriversResource extends DriversResourceBase {

    private BubbleApp app;

    public AppDriversResource(Account account, BubbleApp app) {
        super(account);
        this.app = app;
    }

}
