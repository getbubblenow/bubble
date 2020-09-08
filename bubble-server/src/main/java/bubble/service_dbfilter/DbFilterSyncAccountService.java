/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service_dbfilter;

import bubble.model.account.Account;
import bubble.model.account.AccountPolicy;
import bubble.service.account.SyncAccountService;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Service
public class DbFilterSyncAccountService implements SyncAccountService {

    @Override public void syncAccount(@NonNull final Account account) { notSupported("syncAccount"); }
    @Override public void syncPolicy(@NonNull final Account account, @NonNull final AccountPolicy policy) {
        notSupported("syncPlan");
    }

}
