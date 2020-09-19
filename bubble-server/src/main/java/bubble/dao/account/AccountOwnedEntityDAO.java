/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.account;

import bubble.model.account.Account;
import bubble.model.account.HasAccount;
import bubble.model.account.HasAccountNoName;
import bubble.server.BubbleConfiguration;
import bubble.service.SearchService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.dao.SqlViewSearchableDAO;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static bubble.ApiConstants.BUBBLE_CLOUD_SERVICE_DATA;
import static bubble.ApiConstants.HOME_DIR;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.wizard.model.Identifiable.UUID;

@Slf4j
public abstract class AccountOwnedEntityDAO<E extends HasAccount>
        extends AbstractCRUDDAO<E>
        implements SqlViewSearchableDAO<E> {

    public static final Order PRIORITY_ASC = Order.asc("priority");
    public static final Order PRIORITY_DESC = Order.desc("priority");

    public static final Order NAME_ASC = Order.asc("name");

    @Autowired private BubbleConfiguration configuration;
    @Autowired private SearchService searchService;

    @Getter(lazy=true) private final Boolean hasNameField = !HasAccountNoName.class.isAssignableFrom(getFirstTypeParam(getClass()));

    @Override public E postCreate(E entity, Object context) {
        searchService.flushCache(this);
        return super.postCreate(entity, context);
    }

    @Override public E postUpdate(E entity, Object context) {
        searchService.flushCache(this);
        return super.postUpdate(entity, context);
    }

    public List<E> findByAccount(String accountUuid) { return findByField("account", accountUuid); }

    public E findByAccountAndId(String accountUuid, String id) {
        final E found = findByUniqueFields("account", accountUuid, UUID, id);
        return found != null || !getHasNameField() ? found : findByUniqueFields("account", accountUuid, getNameField(), id);
    }

    protected String getNameField() { return "name"; }

    public E findByAccountOrParentAndId(Account account, String id) {
        final E found = findByAccountAndId(account.getUuid(), id);
        return found != null ? found : findByAccountAndId(account.getParent(), id);
    }

    public E findByAccountAndNameAndParentId(Account account, String id) {
        final E found = findByAccountAndId(account.getParent(), id);
        return found == null ? null : findByAccountAndId(account.getUuid(), found.getName());
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
                + BUBBLE_CLOUD_SERVICE_DATA + File.separator
                + pathMiddle + File.separator
                + sha.substring(0, 2) + File.separator
                + sha.substring(2, 4) + File.separator
                + key);
    }

    public boolean dbFilterIncludeAll() { return false; }

    @Override public void delete(String uuid) {
        super.delete(uuid);
        searchService.flushCache(this);
    }

    @Override public void delete(Collection<E> entities) {
        super.delete(entities);
        searchService.flushCache(this);
    }

    public void forceDelete(String uuid) { delete(uuid); }

}
