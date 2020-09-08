/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.account;

import bubble.model.account.AccountPolicy;
import bubble.service.account.SyncAccountService;
import lombok.NonNull;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.stereotype.Repository;

import static bubble.model.account.AccountDeletionPolicy.full_delete;
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

    @Override public AccountPolicy postUpdate(@NonNull final AccountPolicy policy, @NonNull final Object context) {
        if (context instanceof AccountPolicy) {
            final var previousPolicy = (AccountPolicy) context;
            if (!previousPolicy.skipSync()) {
                final var account = getConfiguration().getBean(AccountDAO.class).findByUuid(policy.getAccount());
                if (account.sync()) {
                    getConfiguration().getBean(SyncAccountService.class).syncPolicy(account, policy);
                }
            }
        }

        return super.postUpdate(policy, context);
    }

    public AccountPolicy findSingleByAccount(String accountUuid) {
        final var found = findByAccount(accountUuid);
        if (found.size() == 1) return found.get(0);

        if (found.size() > 1) {
            die("findSingleByAccount: More than 1 policy found for account " + accountUuid + " - " + found.size());
        }

        // If there's no policy, create one. Note that is account is marked as deleted, the new policy will be with full
        // deletion set in.
        final var newPolicy = new AccountPolicy().setAccount(accountUuid);
        final var account = getConfiguration().getBean(AccountDAO.class).findById(accountUuid);
        if (account.deleted()) newPolicy.setDeletionPolicy(full_delete);
        return create(newPolicy);
    }

}
