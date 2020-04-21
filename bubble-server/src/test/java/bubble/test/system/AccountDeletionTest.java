/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.test.system;

import bubble.dao.account.AccountDAO;
import bubble.test.ActivatedBubbleModelTestBase;
import org.cobbzilla.wizard.model.HashedPassword;
import org.junit.Test;

import static org.junit.Assert.*;

public class AccountDeletionTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-test"; }

    @Test public void testFullAccountDeletion() throws Exception { modelTest("account_deletion/full_delete_account"); }
    @Test public void testBlockAccountDeletion() throws Exception { modelTest("account_deletion/block_delete_account"); }

    @Test public void testDeleteAccountWithPayments() throws Exception {
        final var archivedInfoDAO = getBean(bubble.dao.bill.AccountPaymentArchivedDAO.class);
        assertEquals("Starting database contains some archived payments", 0, archivedInfoDAO.countAll().intValue());

        modelTest("account_deletion/delete_account_with_payments");

        final var accountDAO = getBean(AccountDAO.class);
        final var deletedAccounts = accountDAO.findDeleted();
        // there should be just 1 deleted account - finding it by corresponding hashed password value:
        assertEquals("Wrong number of deleted accounts found", 1, deletedAccounts.size());
        final var deletedAccount = deletedAccounts.get(0);
        assertEquals("Deleted account has wrong hashed password",
                     HashedPassword.DELETED.getHashedPassword(),
                     deletedAccount.getHashedPassword().getHashedPassword());
        assertTrue("Account found with 'deleted' password is not marked as deleted", deletedAccount.deleted());

        // there should be just 1 archived payment info records corresponding to that 1 deleted account
        assertEquals("Archived payments record not created for deleted user", 1, archivedInfoDAO.countAll().intValue());

        final var archivedInfo = archivedInfoDAO.findByAccountUuid(deletedAccount.getUuid());
        assertNotNull("Archived payment info not found for deleted user", archivedInfo);

        final var archivedBills = archivedInfo.getBills();
        assertEquals("Only 1 bill should be in for deleted account", 1, archivedBills.length);

        final var archivedPayments = archivedInfo.getPayments();
        assertEquals("Only 1 payment should be in for deleted account", 1, archivedPayments.length);
        assertEquals("Archived payment should be for archived bill",
                     archivedBills[0].getUuid(), archivedPayments[0].getBill());

        final var archivedPaymentMethods = archivedInfo.getPaymentMethods();
        assertEquals("Only 1 payment method should be in for deleted account", 1, archivedPaymentMethods.length);
        assertEquals("Archived payment method should be for used within archived payment",
                     archivedPayments[0].getPaymentMethod(), archivedPaymentMethods[0].getUuid());
    }
}
