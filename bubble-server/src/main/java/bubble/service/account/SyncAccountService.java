/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.account;

import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import lombok.NonNull;

public interface SyncAccountService {

    void syncAccount(@NonNull final Account account);
    void syncPolicy(@NonNull final Account account, @NonNull final AccountPolicy policy);

}
