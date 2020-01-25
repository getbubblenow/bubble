package bubble.dao.account;

import bubble.model.account.AccountTemplate;

import java.util.List;

import static org.cobbzilla.wizard.model.Identifiable.UUID;

public class AccountOwnedTemplateDAO<E extends AccountTemplate> extends AccountOwnedEntityDAO<E> {

    public List<E> findPublicTemplates(String accountUuid) {
        return findByFields("account", accountUuid, "enabled", true, "template", true);
    }

    public E findPublicTemplate(String parentUuid, String id) {
        final E found = findByUniqueFields("account", parentUuid, "enabled", true, "template", true, UUID, id);
        return found != null ? found : findByUniqueFields("account", parentUuid, "enabled", true, "template", true, getNameField(), id);
    }

}
