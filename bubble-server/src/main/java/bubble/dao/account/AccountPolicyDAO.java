/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.dao.account;

import bubble.model.account.AccountPolicy;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository
public class AccountPolicyDAO extends AccountOwnedEntityDAO<AccountPolicy> {

    @Override protected String getNameField() { return "account"; }

    @Override public Object preCreate(AccountPolicy policy) {
        policy.setUuid(policy.getAccount());
        final ValidationResult result = policy.validate();
        if (result.isInvalid()) throw invalidEx(result);
        return super.preCreate(policy);
    }

    @Override public Object preUpdate(AccountPolicy policy) {
        final ValidationResult result = policy.validate();
        if (result.isInvalid()) throw invalidEx(result);
        return super.preUpdate(policy);
    }

    public AccountPolicy findSingleByAccount(String accountUuid) {
        final List<AccountPolicy> found = findByAccount(accountUuid);
        return found.isEmpty() ? create(new AccountPolicy().setAccount(accountUuid)) : found.size() > 1 ? die("findSingleByAccount: "+found.size()+" found!") : found.get(0);
    }

}
