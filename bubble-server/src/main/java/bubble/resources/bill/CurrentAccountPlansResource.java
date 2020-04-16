/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.bill;

import bubble.model.account.Account;
import bubble.model.bill.AccountPlan;
import bubble.model.cloud.BubbleNetwork;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.util.List;

public class CurrentAccountPlansResource extends AccountPlansResource {

    public CurrentAccountPlansResource(Account account) { super(account); }

    @Override protected List<AccountPlan> list(ContainerRequest ctx) {
        return getDao().findByAccountAndNotDeleted(account.getUuid());
    }

    @Override protected AccountPlan find(ContainerRequest ctx, String id) {
        return getDao().findByAccountAndIdAndNotDeleted(getAccountUuid(ctx), id);
    }

    @Override protected AccountPlan findAlternate(ContainerRequest ctx, String id) {
        // id might be a network uuid
        final String accountUuid = getAccountUuid(ctx);
        final BubbleNetwork network = networkDAO.findByAccountAndId(accountUuid, id);
        return network == null ? null : getDao().findByAccountAndNetworkAndNotDeleted(accountUuid, network.getUuid());
    }

    @Override protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, AccountPlan request) {
        return false;
    }

    @Override protected boolean canUpdate(ContainerRequest ctx, Account caller, AccountPlan found, AccountPlan request) {
        return false;
    }

    @Override protected boolean canDelete(ContainerRequest ctx, Account caller, AccountPlan found) {
        return false;
    }

}
