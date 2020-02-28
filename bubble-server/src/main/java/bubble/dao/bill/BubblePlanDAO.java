/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.dao.bill;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.bill.BubblePlan;
import bubble.model.cloud.BubbleNode;
import bubble.server.BubbleConfiguration;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class BubblePlanDAO extends AccountOwnedEntityDAO<BubblePlan> {

    @Autowired private BubbleConfiguration configuration;

    @Override public boolean dbFilterIncludeAll() { return true; }

    @Override public Order getDefaultSortOrder() { return PRIORITY_ASC; }

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

    public BubblePlan findByName(String name) { return findByUniqueField("name", name); }

    public BubblePlan findById(String id) {
        final BubblePlan plan = findByUuid(id);
        if (plan != null) return plan;
        return findByName(id);
    }

    public Set<String> getSupportedCurrencies () {
        return findAll().stream().map(BubblePlan::getCurrency).collect(Collectors.toSet());
    }

}
