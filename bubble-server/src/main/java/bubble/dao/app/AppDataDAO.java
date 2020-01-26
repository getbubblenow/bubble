package bubble.dao.app;

import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.app.BubbleApp;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.hibernate.criterion.Restrictions.and;
import static org.hibernate.criterion.Restrictions.eq;

@SuppressWarnings("Duplicates")
@Repository @Slf4j
public class AppDataDAO extends AppTemplateEntityDAO<AppData> {

    @Autowired private BubbleAppDAO appDAO;

    @Override protected String getNameField() { return "key"; }

    @Override public Order getDefaultSortOrder() { return Order.asc("key"); }

    public String findValueByAppAndSiteAndKey(String app, String site, String key) {
        final AppData found = filterExpired(findByAppAndSiteAndKey(app, site, key));
        return found != null ? found.getData() : null;
    }

    public AppData findByAppAndSiteAndKey(String app, String site, String key) {
        return filterExpired(findByUniqueFields("app", app, "site", site, "key", key));
    }

    public List<AppData> findByAccountAndAppAndAndKey(String account, String app, String key) {
        return filterExpired(findByFields("account", account, "app", app, "key", key));
    }

    public List<AppData> findEnabledByAccountAndAppAndSite(String account, String app, String site) {
        return filterExpired(findByFields("account", account, "app", app, "site", site, "enabled", true));
    }

    private AppData filterExpired(AppData data) {
        if (data == null) return null;
        if (data.expired()) {
            delete(data.getUuid());
            return null;
        }
        return data;
    }

    private List<AppData> filterExpired(List<AppData> data) {
        data.removeIf(d -> filterExpired(d) == null);
        return data;
    }

    public AppData set(AppData data) {
        final AppData found = findByAppAndSiteAndKey(data.getApp(), data.getSite(), data.getKey());
        if (data.getAccount() == null) {
            // sanity check
            final BubbleApp app = appDAO.findByUuid(data.getApp());
            if (app == null) return die("set: App not found: "+data.getApp());
            data.setAccount(app.getAccount());
        }
        if (found == null) return create(data);

        if (!found.getMatcher().equals(data.getMatcher())) return die("set: matcher mismatch");
        found.update(data);
        return update(found);
    }

    public List<AppData> findByExample(Account account, AppData basis) {
        return findByExample(account, basis, null);
    }

    public List<AppData> findByExample(Account account, AppData basis, String key) {
        final List<Criterion> crits = new ArrayList<>();
        if (account != null) crits.add(eq("account", account.getUuid()));
        if (basis.hasApp()) crits.add(eq("app", basis.getApp()));
        if (basis.hasSite()) crits.add(eq("site", basis.getSite()));
        if (basis.hasMatcher()) crits.add(eq("matcher", basis.getMatcher()));
        if (key != null) crits.add(eq("key", key));
        return list(criteria().add(and(crits.toArray(new Criterion[0]))));
    }

    protected List<AppData> findByDevice(String uuid) { return findByField("device", uuid); }

    public void deleteDevice(String uuid) { delete(findByDevice(uuid)); }

}
