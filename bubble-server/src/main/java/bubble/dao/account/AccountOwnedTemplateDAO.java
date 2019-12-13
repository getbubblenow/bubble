package bubble.dao.account;

import bubble.model.account.AccountTemplate;

import java.util.List;

public class AccountOwnedTemplateDAO<E extends AccountTemplate> extends AccountOwnedEntityDAO<E> {

    public List<E> findPublicTemplates(String parentUuid) {
        return findByFields("account", parentUuid, "enabled", true, "template", true);
    }

    public E findPublicTemplate(String parentUuid, String id) {
        final E found = findByUniqueFields("account", parentUuid, "enabled", true, "template", true, "uuid", id);
        return found != null ? found : findByUniqueFields("account", parentUuid, "enabled", true, "template", true, getNameField(), id);
    }

}
