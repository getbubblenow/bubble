/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.app;

import bubble.dao.device.HasDeviceDAO;
import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.app.BubbleApp;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.SingletonList;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.hibernate.criterion.Restrictions.and;
import static org.hibernate.criterion.Restrictions.eq;

@SuppressWarnings("Duplicates")
@Repository @Slf4j
public class AppDataDAO extends AppTemplateEntityDAO<AppData> implements HasDeviceDAO {

    public static final Order KEY_ASC = Order.asc("key");

    @Autowired private BubbleAppDAO appDAO;

    @Override protected String getNameField() { return "key"; }

    @Override public Order getDefaultSortOrder() { return KEY_ASC; }

    public String findValueByAppAndSiteAndKey(String app, String site, String key) {
        final AppData found = filterExpired(findByAppAndSiteAndKey(app, site, key));
        return found != null ? found.getData() : null;
    }

    public AppData findByAppAndSiteAndKey(String app, String site, String key) {
        return filterExpired(findByUniqueFields("app", app, "site", site, "key", key));
    }

    public AppData findByAppAndSiteAndKeyAndDevice(String app, String site, String key, String device) {
        return filterExpired(findByUniqueFields("app", app, "site", site, "key", key, "device", device));
    }

    public List<AppData> findByAccountAndAppAndAndKey(String account, String app, String key) {
        return filterExpired(findByFields("account", account, "app", app, "key", key));
    }

    public List<AppData> findByAccountAndAppAndAndKeyPrefix(String account, String app, String keyPrefix) {
        return filterExpired(findByFieldsEqualAndFieldLike("account", account, "app", app, "key", keyPrefix+"%"));
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

    private final Map<String, Function<AppData, AppData>> dataSetCallbacks = new HashMap<>();
    public void registerCallback(String appUuid, Function<AppData, AppData> callback) { dataSetCallbacks.put(appUuid, callback); }

    public AppData set(AppData data) {
        final AppData found = findByAppAndSiteAndKey(data.getApp(), data.getSite(), data.getKey());
        if (data.getAccount() == null) {
            // sanity check
            final BubbleApp app = appDAO.findByUuid(data.getApp());
            if (app == null) return die("set: App not found: "+data.getApp());
            data.setAccount(app.getAccount());
        }

        final Function<AppData, AppData> callback = dataSetCallbacks.get(data.getApp());
        log.info("set: found callback="+callback+" for app: "+data.getApp());

        if (found == null) return callback == null ? create(data) : callback.apply(create(data).setCreating(true));

        if (!found.getSite().equals(data.getSite())) return die("set: matcher mismatch: found ("+found.getUuid()+"/"+found.getKey()+") with site "+found.getSite()+", update has site: "+data.getSite());
        found.update(data);
        final AppData updated = update(found);
        return callback != null ? callback.apply(updated) : updated;
    }

    public List<AppData> findByExample(Account account, AppData basis) {
        return findByExample(account, basis, null);
    }

    public List<AppData> findByExample(Account account, AppData basis, String key) {
        // try by uuid first
        final AppData byUuid = findByAccountAndId(account.getUuid(), key);
        if (byUuid != null) return new SingletonList<>(byUuid);

        final List<Criterion> crits = new ArrayList<>();
        if (account != null) crits.add(eq("account", account.getUuid()));
        if (basis.hasApp()) crits.add(eq("app", basis.getApp()));
        if (basis.hasSite()) crits.add(eq("site", basis.getSite()));
        if (basis.hasMatcher()) crits.add(eq("matcher", basis.getMatcher()));
        if (key != null) crits.add(eq("key", key));
        return list(criteria().add(and(crits.toArray(new Criterion[0]))));
    }

    public void deleteDevice(String uuid) {
        final int count = bulkDelete("device", uuid);
        log.info("deleteDevice: deleted "+count+" AppData records for device "+uuid);
    }

    public void deleteApp(String uuid) {
        final int count = bulkDelete("app", uuid);
        log.info("deleteApp: deleted "+count+" AppData records for app "+uuid);
    }

    @Override public void delete(String uuid) {
        final AppData data = findByUuid(uuid);
        if (data == null) return;
        final BubbleApp app = appDAO.findByUuid(data.getApp());
        if (app != null) {
            final Function<AppData, AppData> callback = dataSetCallbacks.get(app.getUuid());
            if (callback != null) {
                callback.apply(data.setDeleting(true));
            }
        }
        super.delete(uuid);
    }
}
