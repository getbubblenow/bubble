/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.stream;

import bubble.model.account.Account;
import bubble.model.app.BubbleApp;

public interface AppPrimerService {

    void primeApps();

    void prime(BubbleApp app);

    void prime(Account account, String app);

}
