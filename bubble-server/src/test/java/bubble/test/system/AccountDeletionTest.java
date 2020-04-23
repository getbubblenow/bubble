/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.test.system;

import bubble.dao.account.AccountDAO;
import bubble.dao.bill.AccountPaymentArchivedDAO;
import bubble.model.account.Account;
import bubble.model.bill.AccountPayment;
import bubble.model.bill.AccountPaymentMethod;
import bubble.model.bill.Bill;
import bubble.test.ActivatedBubbleModelTestBase;
import lombok.NonNull;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AccountDeletionTest extends ActivatedBubbleModelTestBase {

    @Override protected String getManifest() { return "manifest-test"; }

    @Before public void truncatePaymentArchive() {
        final var archivedInfoDAO = getBean(AccountPaymentArchivedDAO.class);
        archivedInfoDAO.delete(archivedInfoDAO.findAll());
    }

    @Test public void testFullAccountDeletion() throws Exception { modelTest("account_deletion/full_delete_account"); }
    @Test public void testBlockAccountDeletion() throws Exception { modelTest("account_deletion/block_delete_account"); }

    @Test public void testBlockDeleteAccountWithPayments() throws Exception {
        checkArchivedPayments(modelTest("account_deletion/block_delete_account_with_payments"));
    }

    @Test public void testFullDeleteAccountWithPayments() throws Exception {
        checkArchivedPayments(modelTest("account_deletion/full_delete_account_with_payments"));
    }

    private void checkArchivedPayments(@NonNull final Map<String, Object> modelTestCtx) {
        final var accountDAO = getBean(AccountDAO.class);
        final var archivedInfoDAO = getBean(AccountPaymentArchivedDAO.class);

        final var deletedAccounts = accountDAO.findDeleted();
        // the account was fully deleted at the end of the JSON test
        assertEquals("Wrong number of deleted accounts found", 0, deletedAccounts.size());
        final var deletedAccount = (Account) modelTestCtx.get("testAccount");

        // there should be just 1 archived payment info records corresponding to that 1 deleted account
        assertEquals("Archived payments record not created for deleted user", 1, archivedInfoDAO.countAll().intValue());

        final var archivedInfo = archivedInfoDAO.findByAccountUuid(deletedAccount.getUuid());
        assertNotNull("Archived payment info not found for deleted user", archivedInfo);

        final var archivedBills = archivedInfo.getBills();
        assertEquals("Only 1 bill should be in for deleted account", 1, archivedBills.length);
        assertEquals("Wrong bill archived", ((Bill[]) modelTestCtx.get("bills"))[0], archivedBills[0]);

        final var archivedPayments = archivedInfo.getPayments();
        assertEquals("Only 1 payment should be in for deleted account", 1, archivedPayments.length);
        assertEquals("Wrong payment archived",
                     ((AccountPayment[]) modelTestCtx.get("payments"))[0], archivedPayments[0]);
        assertEquals("Archived payment should be for archived bill",
                     archivedBills[0].getUuid(), archivedPayments[0].getBill());

        final var archivedPaymentMethods = archivedInfo.getPaymentMethods();
        assertEquals("Only 1 payment method should be in for deleted account", 1, archivedPaymentMethods.length);
        assertEquals("Wrong payment method archived",
                     ((AccountPaymentMethod[]) modelTestCtx.get("paymentMethods"))[0], archivedPaymentMethods[0]);
        assertEquals("Archived payment method should be for used within archived payment",
                     archivedPayments[0].getPaymentMethod(), archivedPaymentMethods[0].getUuid());
    }
}
