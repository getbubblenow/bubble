package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class BubblePlanDAO extends AccountOwnedEntityDAO<BubblePlan> {

    @Autowired private BubbleConfiguration configuration;

    @Override public boolean dbFilterIncludeAll() { return true; }

    @Override public BubblePlan findByUuid(String uuid) {
        final BubblePlan plan = super.findByUuid(uuid);
        if (plan != null) return plan;

        // is this our plan?
        final BubbleNode thisNode = configuration.getThisNode();
        if (thisNode != null && thisNode.hasPlan() && thisNode.getPlan().getUuid().equals(uuid)) {
            return thisNode.getPlan();
        }

        return null;
    }

}
