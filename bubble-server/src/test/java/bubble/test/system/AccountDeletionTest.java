/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.test.system;

import bubble.test.ActivatedBubbleModelTestBase;
import org.junit.Test;

public class AccountDeletionTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-test"; }

    @Test public void testFullAccountDeletion() throws Exception { modelTest("account_deletion/full_delete_account"); }
    @Test public void testBlockAccountDeletion() throws Exception { modelTest("account_deletion/block_delete_account"); }

    @Test public void testDeleteAccountWithPayments() throws Exception {
        modelTest("account_deletion/delete_account_with_payments");

        // TODO: Check if all payment data is archived for the deleted user
    }
}
