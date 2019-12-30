package bubble.dao.account;

import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.account.HasAccountNoName;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.dao.SqlViewSearchableDAO;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.List;

import static bubble.ApiConstants.HOME_DIR;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.hibernate.criterion.Restrictions.eq;
import static org.hibernate.criterion.Restrictions.or;

@Slf4j
public abstract class AccountOwnedEntityDAO<E extends HasAccount>
        extends AbstractCRUDDAO<E>
        implements SqlViewSearchableDAO<E> {

    @Autowired private BubbleConfiguration configuration;

    @Getter(lazy=true) private final Boolean hasNameField = !HasAccountNoName.class.isAssignableFrom(getFirstTypeParam(getClass()));

    public List<E> findByAccount(String accountUuid) { return findByField("account", accountUuid); }

    public E findByAccountAndId(String accountUuid, String id) {
        final E found = findByUniqueFields("account", accountUuid, "uuid", id);
        return found != null || !getHasNameField() ? found : findByUniqueFields("account", accountUuid, getNameField(), id);
    }

    protected String getNameField() { return "name"; }

    public E findByAccountOrParentAndId(Account account, String id) {
        final E found = findByAccountAndId(account.getUuid(), id);
        return found != null ? found : findByAccountAndId(account.getParent(), id);
    }

    public File getFile(String cloudServiceUuid, String key) {
        final String sha = sha256_hex(key);
        final String pathMiddle;
        if (configuration.testMode()) {
            // keep in well known place that won't change across runs. but keys must now be unique across services
            pathMiddle = "_test_";
        } else {
            pathMiddle = cloudServiceUuid;
        }
        return new File(HOME_DIR + File.separator
                + "bubble_cloudServiceData" + File.separator
                + pathMiddle + File.separator
                + sha.substring(0, 2) + File.separator
                + sha.substring(2, 4) + File.separator
                + key);
    }

    public boolean dbFilterIncludeAll() { return false; }

    public void handleAccountDeletion(String accountUuid) {
        findByAccount(accountUuid).forEach(e -> forceDelete(e.getUuid()));
    }

    public void forceDelete(String uuid) { delete(uuid); }

    public List<? extends HasAccount> findByAccountOrParent(Account account) {
        if (account.hasParent()) {
            return list(criteria().add(or(
                    eq("account", account.getUuid()),
                    eq("account", account.getParent()))));
        } else {
            return findByAccount(account.getUuid());
        }
    }
}
