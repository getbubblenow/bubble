package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.AccountPayment;
import bubble.model.bill.AccountPaymentStatus;
import bubble.model.bill.AccountPaymentType;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    public AccountPayment findByAccountAndAccountPlanAndBillAndCreditAppliedSuccess(String accountUuid, String accountPlanUuid, String billUuid) {
        return findByUniqueFields("account", accountUuid,
                "accountPlan", accountPlanUuid,
                "bill", billUuid,
                "type", AccountPaymentType.credit_applied,
                "status", AccountPaymentStatus.success);
    }

}
