/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.test.system;

import bubble.dao.account.AccountDAO;
import bubble.model.bill.AccountPayment;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.Bill;
import bubble.test.ActivatedBubbleModelTestBase;
import org.cobbzilla.wizard.model.HashedPassword;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AccountDeletionTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-test"; }

    @Test public void testFullAccountDeletion() throws Exception { modelTest("account_deletion/full_delete_account"); }
    @Test public void testBlockAccountDeletion() throws Exception { modelTest("account_deletion/block_delete_account"); }

    @Test public void testDeleteAccountWithPayments() throws Exception {
        final var archivedInfoDAO = getBean(bubble.dao.bill.AccountPaymentArchivedDAO.class);
        assertEquals("Starting database contains some archived payments", 0, archivedInfoDAO.countAll().intValue());

        modelTest("account_deletion/delete_account_with_payments");

        assertEquals("Archived payments record not created for deleted user", 1, archivedInfoDAO.countAll().intValue());

        final var accountDAO = getBean(AccountDAO.class);
        // there should be just 1 deleted account - finding it by corresponding hashed password value:
        final var deletedAccount = accountDAO.findByUniqueField("hashedPassword", HashedPassword.DELETED);
        // just in case:
        assertTrue("Account found with 'deleted' password is not marked as deleted", deletedAccount.deleted());

        final var archivedInfo = archivedInfoDAO.findByAccountUuid(deletedAccount.getUuid());
        assertNotNull("Archived payment info not found for deleted user", archivedInfo);

        final List<Bill> archivedBills = archivedInfo.getBills();
        assertEquals("Only 1 bill should be in for deleted account", archivedBills.size());

        final List<AccountPayment> archivedPayments = archivedInfo.getPayments();
        assertEquals("Only 1 bill should be in for deleted account", archivedPayments.size());
        assertEquals("Archived payment should be for archived bill",
                     archivedBills.get(0).getUuid(), archivedPayments.get(0).getBill());

        final List<AccountPaymentMethod> archivedPaymentMethods = archivedInfo.getpaymentMethods();
        assertEquals("Only 1 bill should be in for deleted account", archivedPaymentMethods.size());
        assertEquals("Archived payment method should be for used within archived payment",
                     archivedPayments.get(0).getPaymentMethod(), archivedPaymentMethods.get(0).getUuid());
    }
}
