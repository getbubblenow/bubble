/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.account;

import bubble.model.account.Account;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @Accessors(chain=true)
public class SyncPasswordNotification {

    @Getter @Setter private String accountUuid;
    @Getter @Setter private String hashedPassword;

    public SyncPasswordNotification(Account account) {
        this.accountUuid = account.getUuid();
        this.hashedPassword = account.getHashedPassword().getHashedPassword();
    }

}
