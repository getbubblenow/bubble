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
}
