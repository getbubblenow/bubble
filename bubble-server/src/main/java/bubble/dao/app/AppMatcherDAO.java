/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.app;

import bubble.dao.account.AccountDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.service.stream.AppPrimerService;
import bubble.service.stream.RuleEngineService;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static bubble.model.app.AppMatcher.WILDCARD_FQDN;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.hibernate.criterion.Restrictions.*;

@Repository @Slf4j
public class AppMatcherDAO extends AppTemplateEntityDAO<AppMatcher> {

    @Autowired private AccountDAO accountDAO;
    @Autowired private BubbleAppDAO appDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private AppDataDAO dataDAO;
    @Autowired private RuleEngineService ruleEngineService;
    @Autowired private AppPrimerService primerService;

    @Override public Order getDefaultSortOrder() { return PRIORITY_ASC; }

    public List<AppMatcher> findByAccountAndFqdnAndEnabledAndRequestCheck(String account, String fqdn) {
        return list(criteria().add(
                and(
                        eq("account", account),
                        eq("enabled", true),
                        eq("requestCheck", true),
                        or(
                                eq("fqdn", fqdn),
                                eq("fqdn", WILDCARD_FQDN)
                        ))
        ).addOrder(PRIORITY_ASC));
    }

    public List<AppMatcher> findByAccountAndEnabledAndConnCheck(String account) {
        return findByFields("account", account, "enabled", true, "connCheck", true);
    }

    public List<AppMatcher> findByAccountAndAppAndSite(String accountUuid, String appUuid, String siteUuid) {
        return findByFields("account", accountUuid, "app", appUuid, "site", siteUuid);
    }

    public AppMatcher findByAccountAndAppAndSiteAndId(String accountUuid, String appUuid, String siteUuid, String id) {
        final AppMatcher found = findByUniqueFields("account", accountUuid, "app", appUuid, "site", siteUuid, "name", id);
        return found != null ? found : findByUniqueFields("account", accountUuid, "app", appUuid, "site", siteUuid, "uuid", id);
    }

    @Override public Object preCreate(AppMatcher matcher) {
        if (matcher.getConnCheck() == null) matcher.setConnCheck(false);
        if (matcher.getRequestCheck() == null) matcher.setRequestCheck(false);
        if (matcher.getRequestModifier() == null) matcher.setRequestModifier(false);
        return super.preCreate(matcher);
    }

    @Override public AppMatcher postUpdate(AppMatcher matcher, Object context) {
        final BubbleApp app = appDAO.findByUuid(matcher.getApp());
        if (app == null) return die("postUpdate("+ matcher.getUuid()+"): app not found: "+ matcher.getApp());

        if (app.template()) {
            final AppRule rule = ruleDAO.findByUuid(matcher.getRule());
            if (rule == null) return die("postUpdate("+ matcher.getUuid()+"): rule not found: "+ matcher.getRule());

            for (Account account : accountDAO.findAll()) {
                if (account.getUuid().equals(matcher.getAccount())) {
                    continue;
                }
                final BubbleApp accountApp = appDAO.findByAccountAndId(account.getUuid(), app.getName());
                if (accountApp == null) {
                    // todo: log this?
                    continue;
                }
                final AppMatcher accountMatcher = findByAccountAndAppAndName(account.getUuid(), accountApp.getUuid(), matcher.getName());
                if (accountMatcher == null) {
                    // todo: if they want new stuff, should we create it now?
                    continue;
                }
            }
        }
        ruleEngineService.flushCaches();
        primerService.prime(app);
        return super.postUpdate(matcher, context);
    }

    @Override public void delete(String uuid) {
        final AppMatcher matcher = findByUuid(uuid);
        if (matcher != null) {
            getConfiguration().deleteDependencies(matcher);
            super.delete(uuid);
            ruleEngineService.flushCaches();
        }
    }
}
