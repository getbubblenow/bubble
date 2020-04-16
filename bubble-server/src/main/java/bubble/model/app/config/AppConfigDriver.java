/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.model.app.config;

import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.RuleDriverDAO;
import bubble.model.account.Account;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public interface AppConfigDriver {

    String PARAM_ID = "id";

    Object getView(Account account, BubbleApp app, String view, Map<String, String> params);

    Object takeAppAction(Account account,
                         BubbleApp app,
                         String view,
                         String action,
                         Map<String, String> params,
                         JsonNode data);

    Object takeItemAction(Account account,
                          BubbleApp app,
                          String view,
                          String action,
                          String id,
                          Map<String, String> params,
                          JsonNode data);

    AppRuleDAO getRuleDAO();
    RuleDriverDAO getDriverDAO();

    default AppRule loadRule(Account account, BubbleApp app) { return loadRule(account, app, getRuleDAO()); }

    static AppRule loadRule(Account account, BubbleApp app, AppRuleDAO ruleDAO) {
        final List<AppRule> rules = ruleDAO.findByAccountAndAppAndEnabled(account.getUuid(), app.getUuid());
        if (rules.isEmpty()) return die("loadRule: no rule found");
        if (rules.size() > 1) return die("loadRule: expected only one enabled rule, found "+rules.size());
        return rules.get(0);
    }

}
