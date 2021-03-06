/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.bill;

import bubble.model.bill.Promotion;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.hibernate.criterion.Restrictions.*;

@Repository
public class PromotionDAO extends AbstractCRUDDAO<Promotion> {

    public static final Order PRIORITY_DESC = Order.desc("priority");

    @Override public Order getDefaultSortOrder() { return PRIORITY_DESC; }

    public Promotion findByName(String name) { return findByUniqueField("name", name); }

    public Promotion findById(String id) {
        final Promotion found = findByUuid(id);
        return found != null ? found : findByName(id);
    }

    public Promotion findEnabledAndActiveWithCode(String code, String currency) {
        return filterActive(findByUniqueFields(
                "enabled", true,
                "code", code,
                "referral", false,
                "currency", currency,
                "adminAssignOnly", false));
    }

    public List<Promotion> findEnabledAndActiveWithNoCode(String currency) {
        return filterActive(findByFields(
                "enabled", true,
                "code", null,
                "referral", false,
                "currency", currency,
                "adminAssignOnly", false));
    }

    public List<Promotion> findEnabledAndActiveAndLaunchFailureWithNoCode(String currency) {
        return filterActive(findByFields(
                "enabled", true,
                "code", null,
                "referral", false,
                "launchFailureCredit", true,
                "currency", currency,
                "adminAssignOnly", false));
    }

    public List<Promotion> findVisibleAndEnabledAndActiveWithNoCodeOrWithCode(String code, String currency) {
        if (empty(code)) {
            return filterActive(findByFields(
                    "enabled", true,
                    "code", null,
                    "visible", true,
                    "currency", currency,
                    "adminAssignOnly", false));
        } else {
            return filterActive(list(criteria().add(and(
                    eq("enabled", true),
                    eq("visible", true),
                    eq("currency", currency),
                    or(isNull("code"), eq("code", code))))));
        }
    }

    public List<Promotion> findEnabledAndActiveWithReferral(String currency) {
        return filterActive(findByFields(
                "enabled", true,
                "referral", true,
                "currency", currency,
                "adminAssignOnly", false));
    }

    public Promotion filterActive(Promotion promo) { return promo != null && promo.active() ? promo : null; }

    public List<Promotion> filterActive(List<Promotion> promos) {
        return promos.stream().filter(Promotion::active).collect(Collectors.toList());
    }

}
