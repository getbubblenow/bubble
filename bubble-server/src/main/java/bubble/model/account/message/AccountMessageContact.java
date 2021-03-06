/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.account.message;

import bubble.model.account.AccountContact;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class AccountMessageContact implements Serializable {

    @Getter @Setter private AccountMessage message;
    @Getter @Setter private AccountContact contact;

    public boolean valid () { return message != null && message.hasUuid() && contact != null && contact.hasUuid(); }

    public String key() { return message.redisPrefix() + contact.getUuid(); }

}
