/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.dao.app;

import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.app.AppTemplateEntity;

import java.util.List;

import static org.cobbzilla.wizard.model.Identifiable.UUID;

public class AppTemplateEntityDAO<E extends AppTemplateEntity> extends AccountOwnedTemplateDAO<E> {

    public E findByAccountAndAppAndName(String accountUuid, String appUuid, String name) {
        return findByUniqueFields("account", accountUuid, "app", appUuid, getNameField(), name);
    }

    public List<E> findByAccountAndApp(String account, String app) {
        return findByFields("account", account, "app", app);
    }

    public List<E> findByAccountAndAppAndEnabled(String account, String app) {
        return findByFields("account", account, "app", app, "enabled", true);
    }

    public E findByAccountAndAppAndId(String account, String app, String id) {
        final E found = findByUniqueFields("account", account, "app", app, UUID, id);
        if (found != null) return found;
        return findByUniqueFields("account", account, "app", app, getNameField(), id);
    }

    public List<E> findByAccount(String account) { return findByField("account", account); }

    public List<E> findByApp(String app) { return findByField("app", app); }

}
