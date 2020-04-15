/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.test.system;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountPolicyDAO;
import bubble.model.account.Account;
import bubble.test.ActivatedBubbleModelTestBase;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.HashedPassword;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

@Slf4j
public class AccountDeletionTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-test"; }

    @Before public void resetRootPassword() {
        // reset root password, some tests change it
        final AccountDAO accountDAO = getConfiguration().getBean(AccountDAO.class);
        final Account rootUser = accountDAO.findFirstAdmin();
        accountDAO.update(rootUser.setHashedPassword(new HashedPassword(ROOT_PASSWORD)));
    }

    @Test public void testFullAccountDeletion() throws Exception { modelTest("account_deletion/full_delete_account"); }
    @Test public void testBlockAccountDeletion() throws Exception {
        modelTest("account_deletion/block_delete_account");

        final var accPolicyDAO = getConfiguration().getBean(AccountPolicyDAO.class);
        final var policies = accPolicyDAO.findAll();
        assertEquals("Not all objects removed for deleted account", 1, policies.size());

        final var accDAO = getConfiguration().getBean(AccountDAO.class);
        final var acc = accDAO.findById(policies.get(0).getAccount());
        assertEquals("The one policy should be for the only existing account", "root", acc.getName());
    }

}
