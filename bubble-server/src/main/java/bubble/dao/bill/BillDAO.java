package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.*;
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

    public Bill createFirstBill(BubblePlan plan, AccountPlan accountPlan) {
        return create(newBill(plan, accountPlan, accountPlan.getCtime()));
    }

    public Bill newBill(BubblePlan plan, AccountPlan accountPlan, long periodStartMillis) {
        final BillPeriod period = plan.getPeriod();
        return new Bill()
                    .setAccount(accountPlan.getAccount())
                    .setPlan(plan.getUuid())
                    .setAccountPlan(accountPlan.getUuid())
                    .setPrice(plan.getPrice())
                    .setCurrency(plan.getCurrency())
                    .setPeriodLabel(period.periodLabel(periodStartMillis))
                    .setPeriodStart(period.periodStart(periodStartMillis))
                    .setPeriodEnd(period.periodEnd(periodStartMillis))
                    .setQuantity(1L)
                    .setType(BillItemType.compute)
                    .setStatus(BillStatus.unpaid);
    }

}
