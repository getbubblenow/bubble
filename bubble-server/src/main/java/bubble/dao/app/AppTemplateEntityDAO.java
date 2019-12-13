package bubble.dao.app;

import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.app.AppTemplateEntity;

import java.util.List;

public class AppTemplateEntityDAO<E extends AppTemplateEntity> extends AccountOwnedTemplateDAO<E> {

    public E findByAccountAndAppAndName(String accountUuid, String appUuid, String name) {
        return findByUniqueFields("account", accountUuid, "app", appUuid, getNameField(), name);
    }

    public List<E> findByAccountAndApp(String account, String app) {
        return findByFields("account", account, "app", app);
    }

    public E findByAccountAndAppAndId(String account, String app, String id) {
        final E found = findByUniqueFields("account", account, "app", app, "uuid", id);
        if (found != null) return found;
        return findByUniqueFields("account", account, "app", app, getNameField(), id);
    }

    public List<E> findByAccount(String account) { return findByField("account", account); }

    public List<E> findByApp(String app) { return findByField("app", app); }

}
