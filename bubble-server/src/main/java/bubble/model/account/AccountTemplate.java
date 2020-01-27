package bubble.model.account;

import bubble.dao.account.AccountOwnedTemplateDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.cobbzilla.util.daemon.ZillaRuntime.bool;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

public interface AccountTemplate extends HasAccount {

    default boolean template() { return bool(getTemplate()); }
    default boolean enabled() { return bool(getEnabled()); }
    default boolean disabled() { return !enabled(); }

    Boolean getEnabled();
    Boolean getTemplate();
    <T extends AccountTemplate> T setTemplate(Boolean t);

    Logger log = LoggerFactory.getLogger(AccountTemplate.class);

    interface CopyTemplate<E extends AccountTemplate> {
        default E preCreate (E parentEntity, E accountEntity) { return accountEntity; }
        default void postCreate (E parentEntity, E accountEntity) { }
    }

    static <E extends AccountTemplate> void copyTemplateObjects (String accountUuid,
                                                                 String parentAccountUuid,
                                                                 AccountOwnedTemplateDAO<E> dao) {
        copyTemplateObjects(accountUuid, parentAccountUuid, dao, null);
    }

    static <E extends AccountTemplate> void copyTemplateObjects (String accountUuid,
                                                                 String parentAccountUuid,
                                                                 AccountOwnedTemplateDAO<E> dao,
                                                                 CopyTemplate<E> copy) {
        try {
            for (E parentEntity : dao.findPublicTemplates(parentAccountUuid)) {
                E accountEntity = dao.findByAccountAndId(accountUuid, parentEntity.getName());
                if (accountEntity == null) {
                    accountEntity = ((E) instantiate(parentEntity.getClass(), parentEntity)).setAccount(accountUuid);
                    if (copy != null) accountEntity= copy.preCreate(parentEntity, accountEntity);
                    if (accountEntity == null) {
                        log.warn("copyTemplateObjects: preCreate returned null for parentEntity: " + parentEntity);
                        return;
                    }
                    accountEntity= dao.create(accountEntity.setTemplate(false));
                }
                if (copy != null) copy.postCreate(parentEntity, accountEntity);
            }
        } catch (Exception e) {
            die("copyTemplateObjects: dao="+dao.getClass().getSimpleName()+": "+e, e);
        }
    }

}
