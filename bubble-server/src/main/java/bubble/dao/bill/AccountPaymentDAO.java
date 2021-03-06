/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.AccountPayment;
import bubble.model.bill.AccountPaymentStatus;
import bubble.model.bill.AccountPaymentType;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.hibernate.criterion.Restrictions.*;

@Repository
public class AccountPaymentDAO extends AccountOwnedEntityDAO<AccountPayment> {

    // newest first
    @Override public Order getDefaultSortOrder() { return ORDER_CTIME_DESC; }

    public List<AccountPayment> findByAccountAndPlan(String accountUuid, String accountPlanUuid) {
        return findByFields("account", accountUuid, "plan", accountPlanUuid);
    }

    public List<AccountPayment> findByAccountAndBill(String accountUuid, String billUuid) {
        return findByFields("account", accountUuid, "bill", billUuid);
    }

    public List<AccountPayment> findByAccountAndAccountPlan(String accountUuid, String accountPlanUuid) {
        return findByFields("account", accountUuid, "accountPlan", accountPlanUuid);
    }

    public List<AccountPayment> findByAccountAndAccountPlanAndPaid(String accountUuid, String accountPlanUuid) {
        return list(criteria().add(and(
                eq("account", accountUuid),
                eq("accountPlan", accountPlanUuid),
                eq("status", AccountPaymentStatus.success),
                or(eq("type", AccountPaymentType.payment),
                        eq("type", AccountPaymentType.credit_applied)))));

    }

    public List<AccountPayment> findByAccountAndAccountPlanAndBill(String accountUuid, String accountPlanUuid, String billUuid) {
        return findByFields("account", accountUuid, "accountPlan", accountPlanUuid, "bill", billUuid);
    }

    public AccountPayment findByAccountAndAccountPlanAndBillAndPaymentSuccess(String accountUuid, String accountPlanUuid, String billUuid) {
        return findByUniqueFields("account", accountUuid,
                "accountPlan", accountPlanUuid,
                "bill", billUuid,
                "type", AccountPaymentType.payment,
                "status", AccountPaymentStatus.success);
    }

    public List<AccountPayment> findByAccountAndAccountPlanAndPaymentSuccess(String accountUuid, String accountPlanUuid) {
        return findByFields("account", accountUuid,
                "accountPlan", accountPlanUuid,
                "type", AccountPaymentType.payment,
                "status", AccountPaymentStatus.success);
    }

    public List<AccountPayment> findByAccountAndAccountPlanAndBillAndPaid(String accountUuid, String accountPlanUuid, String billUuid) {
        return list(criteria().add(and(
                eq("account", accountUuid),
                eq("accountPlan", accountPlanUuid),
                eq("bill", billUuid),
                eq("status", AccountPaymentStatus.success),
                or(eq("type", AccountPaymentType.payment),
                        eq("type", AccountPaymentType.credit_applied)))));
    }

    public List<AccountPayment> findByAccountAndPaymentSuccess(String accountUuid) {
        return findByFields("account", accountUuid,
                "type", AccountPaymentType.payment,
                "status", AccountPaymentStatus.success);
    }

    public List<AccountPayment> findByAccountAndAccountPlanAndBillAndCreditAppliedSuccess(String accountUuid, String accountPlanUuid, String billUuid) {
        return findByFields("account", accountUuid,
                "accountPlan", accountPlanUuid,
                "bill", billUuid,
                "type", AccountPaymentType.credit_applied,
                "status", AccountPaymentStatus.success);
    }

}
