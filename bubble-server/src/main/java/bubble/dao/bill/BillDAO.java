/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.*;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.hibernate.criterion.Restrictions.*;

@Repository
public class BillDAO extends AccountOwnedEntityDAO<Bill> {

    // newest first
    @Override public Order getDefaultSortOrder() { return ORDER_CTIME_DESC; }

    @Override public Object preCreate(Bill bill) {
        return super.preCreate(bill.setNotified(false));
    }

    // todo: make this more efficient, use "COUNT"
    public int countByAccount(String accountUuid) { return findByAccount(accountUuid).size(); }

    public List<Bill> findByAccountAndAccountPlan(String accountUuid, String accountPlanUuid) {
        return findByFields("account", accountUuid, "accountPlan", accountPlanUuid);
    }

    public List<Bill> findNotifiedByAccountAndAccountPlan(String accountUuid, String accountPlanUuid) {
        return findByFields("account", accountUuid, "accountPlan", accountPlanUuid, "notified", true);
    }

    public Bill findMostRecentBillForAccountPlan(String accountPlanUuid) {
        final List<Bill> bills = findByField("accountPlan", accountPlanUuid);
        return bills.isEmpty() ? null : bills.get(0);
    }

    public Bill findFirstUnpaidByAccountPlan(String accountPlanUuid) {
        final List<Bill> unpaid = findUnpaidByAccountPlan(accountPlanUuid);
        return unpaid.isEmpty() ? null : unpaid.get(0);
    }

    public List<Bill> findUnpaidByAccountPlan(String accountPlanUuid) {
        return list(criteria().add(and(
                eq("accountPlan", accountPlanUuid),
                ne("status", BillStatus.paid)))
                .addOrder(ORDER_CTIME_ASC));
    }

    public List<Bill> findUnpaidAndDueByAccountPlan(BubblePlan plan, String accountPlanUuid) {
        final long now = now();
        return list(criteria().add(and(
                eq("accountPlan", accountPlanUuid),
                ne("status", BillStatus.paid)))
                .addOrder(ORDER_CTIME_ASC)).stream()
                .filter(b -> b.isDue(plan, now))
                .collect(Collectors.toList());
    }

    public List<Bill> findUnpaidByAccount(String accountUuid) {
        return list(criteria().add(and(
                eq("account", accountUuid),
                ne("status", BillStatus.paid))));
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
                    .setTotal(plan.getPrice())
                    .setCurrency(plan.getCurrency())
                    .setPeriodLabel(period.periodLabel(periodStartMillis))
                    .setPeriodStart(period.periodStart(periodStartMillis))
                    .setPeriodEnd(period.periodEnd(periodStartMillis))
                    .setStatus(BillStatus.unpaid);
    }

}
