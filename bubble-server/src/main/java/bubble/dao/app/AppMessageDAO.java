package bubble.dao.app;

import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.app.AppMessage;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Repository;

import java.util.List;

import static bubble.server.BubbleConfiguration.getDEFAULT_LOCALE;

@Repository
public class AppMessageDAO extends AccountOwnedTemplateDAO<AppMessage> {

    @Override public Boolean getHasNameField() { return false; }

    @Override public Order getDefaultSortOrder() { return Order.asc("priority"); }

    public AppMessage findByAccountAndAppAndLocale (String account, String app, String locale) {
        return findByUniqueFields("account", account, "app", app, "locale", locale, "enabled", true);
    }

    public List<AppMessage> findByAccountAndApp (String account, String app) {
        return findByFields("account", account, "app", app, "enabled", true);
    }

    public AppMessage findByAccountAndAppAndDefaultLocale(String account, String app) {
        return findByUniqueFields("account", account, "app", app, "locale", getDEFAULT_LOCALE(), "enabled", true);
    }

    public AppMessage findByAccountAndAppAndHighestPriority(String account, String app) {
        final List<AppMessage> messages = findByFields("account", account, "app", app, "enabled", true);
        return messages.isEmpty() ? null : messages.get(0);
    }

    public List<AppMessage> findByApp(String app) { return findByField("app", app); }

}
