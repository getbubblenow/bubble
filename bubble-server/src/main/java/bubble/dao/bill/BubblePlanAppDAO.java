package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.BubblePlanApp;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BubblePlanAppDAO extends AccountOwnedEntityDAO<BubblePlanApp> {

    public List<BubblePlanApp> findByAccountAndPlan(String account, String bubblePlan) {
        return findByFields("account", account, "plan", bubblePlan);
    }

}
