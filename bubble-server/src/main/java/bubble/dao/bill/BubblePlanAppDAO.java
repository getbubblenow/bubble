/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.dao.app.BubbleAppDAO;
import bubble.model.app.BubbleApp;
import bubble.model.bill.BubblePlan;
import bubble.model.bill.BubblePlanApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BubblePlanAppDAO extends AccountOwnedEntityDAO<BubblePlanApp> {

    @Autowired private BubbleAppDAO appDAO;

    @Override public boolean dbFilterIncludeAll() { return true; }

    public List<BubblePlanApp> findByPlan(String bubblePlanUuid) {
        return findByField("plan", bubblePlanUuid);
    }

    public BubblePlanApp findByPlanAndId(BubblePlan plan, String id) {
        final BubblePlanApp planApp = findByUniqueFields("plan", plan.getUuid(), "app", id);
        if (planApp != null) return planApp;

        final BubbleApp app = appDAO.findByAccountAndId(plan.getAccount(), id);
        return app == null ? null : findByUniqueFields("plan", plan.getUuid(), "app", app.getUuid());
    }

}
