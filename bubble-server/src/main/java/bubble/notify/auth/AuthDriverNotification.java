/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.notify.auth;

import bubble.model.account.Account;
import bubble.model.account.AccountContact;
import bubble.model.account.message.AccountMessage;
import bubble.notify.SynchronousNotification;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;

@Accessors(chain=true)
public class AuthDriverNotification extends SynchronousNotification {

    @Getter @Setter private String authService;
    @Getter @Setter private Account account;
    @Getter @Setter private AccountMessage message;
    @Getter @Setter private AccountContact contact;

    @Getter(lazy=true) private final String cacheKey
            = hashOf(authService, account != null ? account.getUuid() : null, message != null ? message.getCacheKey() : null, contact != null ? contact.getCacheKey() : null);

}
