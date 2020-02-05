package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.app.BubbleAppDAO;
import bubble.model.app.BubbleApp;
import bubble.model.bill.BubblePlanApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BubblePlanAppDAO extends AccountOwnedEntityDAO<BubblePlanApp> {

    @Autowired private BubbleAppDAO appDAO;

    @Override public boolean dbFilterIncludeAll() { return true; }

    public List<BubblePlanApp> findByPlan(String bubblePlan) {
        return findByField("plan", bubblePlan);
    }

    public BubblePlanApp findByAccountAndPlanAndId(String account, String bubblePlan, String id) {
        final BubblePlanApp planApp = findByUniqueFields("plan", bubblePlan, "app", id);
        if (planApp != null) return planApp;

        final BubbleApp app = appDAO.findByAccountAndId(account, id);
        return app == null ? null : findByUniqueFields("plan", bubblePlan, "app", app.getUuid());
    }

}
