/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources.account;

import bubble.dao.account.AccountSshKeyDAO;
import bubble.model.account.Account;
import bubble.model.account.AccountSshKey;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;

import java.util.List;

import static org.cobbzilla.util.http.HttpMethods.PUT;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Slf4j
public class AccountSshKeysResource extends AccountOwnedResource<AccountSshKey, AccountSshKeyDAO> {

    public AccountSshKeysResource(Account account) { super(account); }

    @Override protected AccountSshKey find(ContainerRequest ctx, String id) {
        final AccountSshKey found = super.find(ctx, id);
        return found == null ? null : found.setSshPublicKey(null);
    }

    @Override protected List<AccountSshKey> list(ContainerRequest ctx) {
        final List<AccountSshKey> found = super.list(ctx);
        for (AccountSshKey k : found) k.setSshPublicKey(null);
        return found;
    }

    @Override protected AccountSshKey findAlternate(ContainerRequest ctx, AccountSshKey request) {
        return getDao().findByAccountAndHash(getAccountUuid(ctx), request.getSshPublicKeyHash());
    }

    @Override protected boolean canUpdate(ContainerRequest ctx, Account caller, AccountSshKey found, AccountSshKey request) {
        // cannot use create method (PUT) to update a key
        if (found != null && ctx.getMethod().equals(PUT)) throw invalidEx("err.sshPublicKey.alreadyExists");
        return true;
    }

}
