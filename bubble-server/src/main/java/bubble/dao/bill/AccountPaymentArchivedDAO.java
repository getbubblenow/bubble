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

import java.util.List;

@Repository
public class AccountPaymentArchivedDAO
        extends AbstractCRUDDAO<AccountPaymentArchived>
        implements SqlViewSearchableDAO<AccountPaymentArchived> {

    // newest first
    @Override public Order getDefaultSortOrder() { return ORDER_CTIME_DESC; }

    @NonNull public List<AccountPaymentArchived> findByAccountName(@NonNull final String accountName) {
        return findByField("accountName", accountName);
    }

    /**
     * Anonymize this object. This is needed when client requires and signs/waives his/her right to sue in the future.
     */
    public void anonymizeForAccountName(@NonNull final String accountName) {
        // TODO: what about paymentMethodMaskedInfo, bubblePlanName and billPeriodStart. Do those fields contain any
        //       user info and names set up by the user?
        bulkUpdate(new String[] { "accountName", "accountPlanName" }, new String[] { "anonymous", "anonymized" },
                   new String[] { "accountName" }, new String[] { accountName });
    }
}
