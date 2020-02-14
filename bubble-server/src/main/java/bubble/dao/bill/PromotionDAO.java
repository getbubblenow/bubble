package bubble.dao.bill;

import bubble.model.bill.Promotion;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    public Promotion findEnabledWithCode(String code) {
        return findByUniqueFields("enabled", true, "code", code);
    }

    public Promotion findEnabledWithNoCode() {
        final List<Promotion> promos = findByFields("enabled", true, "code", null);
        return empty(promos) ? null : promos.get(0);
    }

    public List<Promotion> findEnabledAndNoCodeOrWithCode(String code) {
        if (empty(code)) {
            return findByFields("enabled", true, "code", null);
        } else {
            return list(criteria().add(and(
                    eq("enabled", true),
                    or(isNull("code"), eq("code", code)))));
        }
    }

    public Promotion findReferralPromotion() {
        final List<Promotion> referrals = findByField("referral", true);
        return empty(referrals) ? null : referrals.get(0);
    }

}
