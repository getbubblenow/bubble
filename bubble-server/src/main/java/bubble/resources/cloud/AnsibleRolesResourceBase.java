/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.dao.cloud.AnsibleRoleDAO;
import bubble.model.account.Account;
import bubble.model.cloud.AnsibleRole;
import bubble.resources.account.AccountOwnedTemplateResource;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

public class AnsibleRolesResourceBase extends AccountOwnedTemplateResource<AnsibleRole, AnsibleRoleDAO> {

    public AnsibleRolesResourceBase(Account account) { super(account); }

    @Override protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, AnsibleRole request) {
        // ensure a role with the same name/version does not exist for this account
        final AnsibleRole existing = getDao().findByAccountAndId(caller.getUuid(), request.getName());
        if (existing != null) throw invalidEx("err.role.exists", "A role exists with name: "+request.getName());
        return super.canCreate(req, ctx, caller, request);
    }

    @Override protected boolean canUpdate(ContainerRequest ctx, Account caller, AnsibleRole found, AnsibleRole request) {
        // roles cannot be updated
        return false;
    }

}
