package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.AccountPayment;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AccountPaymentDAO extends AccountOwnedEntityDAO<AccountPayment> {

    public List<AccountPayment> findByAccountAndPlan(String accountUuid, String accountPlanUuid) {
        return findByFields("account", accountUuid, "plan", accountPlanUuid);
    }

    public List<AccountPayment> findByAccountAndBill(String accountUuid, String billUuid) {
        return findByFields("account", accountUuid, "bill", billUuid);
    }
}
