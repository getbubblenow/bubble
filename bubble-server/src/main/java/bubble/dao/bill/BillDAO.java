package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.Bill;
import bubble.model.bill.BillStatus;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BillDAO extends AccountOwnedEntityDAO<Bill> {

    // newest first
    @Override public Order getDefaultSortOrder() { return Order.desc("ctime"); }

    public List<Bill> findByAccountAndAccountPlan(String accountUuid, String accountPlanUuid) {
        return findByFields("account", accountUuid, "accountPlan", accountPlanUuid);
    }

    public Bill findMostRecentBillForAccountPlan(String accountPlanUuid) {
        final List<Bill> bills = findByField("accountPlan", accountPlanUuid);
        return bills.isEmpty() ? null : bills.get(0);
    }

    public List<Bill> findUnpaidByAccountPlan(String accountPlanUuid) {
        return findByFields("accountPlan", accountPlanUuid, "status", BillStatus.unpaid);
    }

}
