/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.app;

import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;

import java.util.function.Function;

public interface HasAppDataCallback {

    void prime(Account account, BubbleApp app, BubbleConfiguration configuration);

    Function<AppData, AppData> createCallback(Account account, BubbleApp app, BubbleConfiguration configuration);

}
