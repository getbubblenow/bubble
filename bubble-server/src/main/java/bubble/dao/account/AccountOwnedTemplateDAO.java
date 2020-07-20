/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.account;

import bubble.model.account.AccountTemplate;

import java.util.List;

import static org.cobbzilla.wizard.model.Identifiable.UUID;

public class AccountOwnedTemplateDAO<E extends AccountTemplate> extends AccountOwnedEntityDAO<E> {

    public List<E> findPublicTemplates(String accountUuid) {
        return findByFields("account", accountUuid, "enabled", true, "template", true);
    }

    public List<E> findPublicTemplatesByApp(String accountUuid, String appUuid) {
        return findByFields("account", accountUuid, "app", appUuid, "enabled", true, "template", true);
    }

    public E findPublicTemplate(String accountUuid, String id) {
        final E found = findByUniqueFields("account", accountUuid, "enabled", true, "template", true, UUID, id);
        return found != null ? found : findByUniqueFields("account", accountUuid, "enabled", true, "template", true, getNameField(), id);
    }

    public E findPublicTemplateByName(String accountUuid, String name) {
        return findByUniqueFields("account", accountUuid, "enabled", true, "template", true, "name", name);
    }

}
