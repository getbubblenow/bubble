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
import bubble.model.app.RuleDriver;
import bubble.rule.AppRuleDriver;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;

public abstract class AppConfigDriverBase implements AppConfigDriver {

    @Autowired @Getter protected RuleDriverDAO driverDAO;
    @Autowired @Getter protected AppRuleDAO ruleDAO;

    protected RuleDriver loadDriver(Account account, AppRule rule, Class<? extends AppRuleDriver> expectedDriverClass) {
        return loadDriver(account, rule, getDriverDAO(), expectedDriverClass);
    }

    protected RuleDriver loadDriver(Account account, AppRule rule, RuleDriverDAO driverDAO, Class<? extends AppRuleDriver> expectedDriverClass) {
        final RuleDriver driver = driverDAO.findByAccountAndId(account.getUuid(), rule.getDriver());
        if (driver == null || !driver.getDriverClass().equals(expectedDriverClass.getName())) {
            return die("expected BubbleBlockRuleDriver");
        }
        return driver;
    }

    protected <T> T getConfig (Account account, BubbleApp app, Class<? extends AppRuleDriver> driverClass, Class<T> configClass) {
        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, driverClass); // validate proper driver
        return json(rule.getConfigJson(), configClass);
    }
}
