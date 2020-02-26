/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources.account;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

public class ReadOnlyAccountOwnedResource<E extends HasAccount, DAO extends AccountOwnedEntityDAO<E>>
        extends AccountOwnedResource<E, DAO> {

    public ReadOnlyAccountOwnedResource(Account account) { super(account); }

    @Override protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, E request) { return false; }
    @Override protected boolean canUpdate(ContainerRequest ctx, Account caller, E found, E request) { return false; }
    @Override protected boolean canDelete(ContainerRequest ctx, Account caller, E found) { return false; }

}
