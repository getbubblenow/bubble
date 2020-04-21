/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.dao.bill;

import bubble.model.bill.AccountPaymentArchived;
import lombok.NonNull;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.dao.SqlViewSearchableDAO;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

@Repository
public class AccountPaymentArchivedDAO
        extends AbstractCRUDDAO<AccountPaymentArchived>
        implements SqlViewSearchableDAO<AccountPaymentArchived> {

    // newest first
    @Override public Order getDefaultSortOrder() { return ORDER_CTIME_DESC; }

    public AccountPaymentArchived findByAccountUuid(@NonNull final String accountUuid) {
        return findByUniqueField("accountUuid", accountUuid);
    }

    @NonNull public AccountPaymentArchived createForAccount(@NonNull final String accountUuid) {
        final var allBills = getConfiguration().getBean(BillDAO.class).findByAccount(accountUuid);
        final var allPayments = getConfiguration().getBean(AccountPaymentDAO.class).findByAccount(accountUuid);
        final var allMethods = getConfiguration().getBean(AccountPaymentMethodDAO.class).findByAccount(accountUuid);

        return create(new AccountPaymentArchived().setAccountUuid(accountUuid)
                                                  .setBills(allBills)
                                                  .setPayments(allPayments)
                                                  .setPaymentMethods(allMethods));
    }
}
