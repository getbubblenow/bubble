/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.test.system;

import bubble.dao.account.AccountDAO;
import bubble.model.account.Account;
import bubble.test.ActivatedBubbleModelTestBase;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.HashedPassword;
import org.junit.Before;
import org.junit.Test;

@Slf4j
public class AuthTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-test"; }

    @Before public void resetRootPassword () {
        // reset root password, some tests change it
        final AccountDAO accountDAO = getConfiguration().getBean(AccountDAO.class);
        final Account rootUser = accountDAO.findFirstAdmin();
        accountDAO.update(rootUser.setHashedPassword(new HashedPassword(ROOT_PASSWORD)));
    }

    @Test public void testAccountDeletion () throws Exception { modelTest("auth/delete_account"); }
    @Test public void testBasicAuth () throws Exception { modelTest("auth/basic_auth"); }
    @Test public void testAccountCrud () throws Exception { modelTest("auth/account_crud"); }
    @Test public void testDeviceCrud () throws Exception { modelTest("auth/device_crud"); }
    @Test public void testRegistration () throws Exception { modelTest("auth/account_registration"); }
    @Test public void testForgotPassword () throws Exception { modelTest("auth/forgot_password"); }
    @Test public void testChangePassword () throws Exception { modelTest("auth/change_password"); }
    @Test public void testChangeAdminPassword () throws Exception { modelTest("auth/change_admin_password"); }
    @Test public void testMultifactorAuth () throws Exception { modelTest("auth/multifactor_auth"); }
    @Test public void testDownloadAccount () throws Exception { modelTest("auth/download_account"); }
    @Test public void testNetworkAuth () throws Exception { modelTest("auth/network_auth"); }

}
