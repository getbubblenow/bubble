package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.Bill;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BillDAO extends AccountOwnedEntityDAO<Bill> {

    public List<Bill> findByAccountAndPlan(String accountUuid, String accountPlanUuid) {
        return findByFields("account", accountUuid, "plan", accountPlanUuid);
    }

}
