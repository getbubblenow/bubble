/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.resources.account;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import org.glassfish.jersey.server.ContainerRequest;

import static org.cobbzilla.wizard.resources.ResourceUtil.*;

public abstract class AccountOwnedTemplateResource<E extends HasAccount, DAO extends AccountOwnedEntityDAO<E>>
        extends AccountOwnedResource<E, DAO> {

    public AccountOwnedTemplateResource(Account account) { super(account); }

    protected String getAccountUuid(ContainerRequest ctx) {
        // if this was created with a "null" account, that means expose parent account's templates in read-only mode
        if (account != null) return account.getUuid();
        final Account caller = userPrincipal(ctx);
        if (!caller.hasParent()) {
            if (caller.admin()) return caller.getUuid(); // admin without parent is root user, is OK
            throw invalidEx("err.account.noParent");
        }
        return caller.getParent();
    }

    @Override protected boolean isReadOnly(ContainerRequest ctx) {
        // admins are OK to do anything
        final Account caller = userPrincipal(ctx);
        if (caller.admin()) return false;

        if (account != null) {
            if (caller.getUuid().equals(account.getUuid())) return false; // belongs to us
            throw forbiddenEx(); // not ours!
        }

        return true; // otherwise read only
    }

}
