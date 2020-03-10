/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.dao.app;

import bubble.dao.account.AccountDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.service.stream.RuleEngineService;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.wizard.model.Identifiable.MTIME;
import static org.hibernate.criterion.Restrictions.*;

@Repository @Slf4j
public class AppMatcherDAO extends AppTemplateEntityDAO<AppMatcher> {

    @Autowired private AccountDAO accountDAO;
    @Autowired private BubbleAppDAO appDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private RuleEngineService ruleEngineService;

    @Override public Order getDefaultSortOrder() { return PRIORITY_ASC; }

    public List<AppMatcher> findByAccountAndFqdnAndEnabled(String account, String fqdn) {
        return list(criteria().add(
                and(
                        eq("account", account),
                        eq("enabled", true),
                        eq("passthru", false),
                        or(
                                eq("fqdn", fqdn),
                                eq("fqdn", "*")
                        ))
        ).addOrder(PRIORITY_ASC));
    }

    public List<AppMatcher> findByAccountAndEnabledAndPassthru(String account) {
        return findByFields("account", account, "enabled", true, "passthru", true);
    }

    public List<AppMatcher> findAllChangesSince(Long lastMod) {
        return list(criteria().add(gt(MTIME, lastMod)));
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
                if (account.wantsAppUpdates()) {
                    accountMatcher.update(matcher);
                    update(accountMatcher);
                } else {
                    appDAO.update(accountApp.setNeedsUpdate(true));
                }
            }
        }
        ruleEngineService.flushCaches();
        return super.postUpdate(matcher, context);
    }

    @Override public void delete(String uuid) {
        super.delete(uuid);
        ruleEngineService.flushCaches();
    }
}
