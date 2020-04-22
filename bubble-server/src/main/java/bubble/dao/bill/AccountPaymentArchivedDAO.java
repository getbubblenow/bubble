/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.bill;

import bubble.model.account.Account;
import bubble.model.bill.AccountPaymentArchived;
import lombok.NonNull;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.dao.SqlViewSearchableDAO;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Repository
public class AccountPaymentArchivedDAO
        extends AbstractCRUDDAO<AccountPaymentArchived>
        implements SqlViewSearchableDAO<AccountPaymentArchived> {

    // newest first
    @Override public Order getDefaultSortOrder() { return ORDER_CTIME_DESC; }

    public AccountPaymentArchived findByAccountUuid(@NonNull final String accountUuid) {
        return findByUniqueField("accountUuid", accountUuid);
    }

    @NonNull public AccountPaymentArchived createForAccount(@NonNull final Account account) {
        final var allBills = getConfiguration().getBean(BillDAO.class).findByAccount(account.getUuid());
        final var allPayments = getConfiguration().getBean(AccountPaymentDAO.class).findByAccount(account.getUuid());
        final var allMethods = getConfiguration().getBean(AccountPaymentMethodDAO.class)
                                                 .findByAccount(account.getUuid());

        // Payment info should be present and archived only for currently non deleted account. So, the first deletion
        // request will archive those. Any call after that for already deleted account that has some payment info is
        // strange and is most probably result of an error - deleted account should not be able to create any payment
        // records.
        if (account.deleted()) {
            if (allBills.size() + allPayments.size() + allMethods.size() > 0) {
                return die("Payment records present for already deleted account " + account.getUuid());
                // Stopping further execution to avoid loss of data. Check these payment entries and then manually
                // decide what to do with those. Call delete again after these are cleared from database.
            }
            // else, just return already existing entry. Note that any deleted account should have an entry here, while
            // such entries might have empty arrays for bills, payment and payment methods.
            return findByAccountUuid(account.getUuid());
        }
        // Finally, create new entry here only if this is the first deletion call for the specified account:
        return create(new AccountPaymentArchived().setAccountUuid(account.getUuid())
                                                  .setBills(allBills)
                                                  .setPayments(allPayments)
                                                  .setPaymentMethods(allMethods));
    }
}
