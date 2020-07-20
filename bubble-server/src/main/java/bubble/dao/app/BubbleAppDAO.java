/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.app;

import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.app.BubbleApp;
import bubble.service.stream.RuleEngineService;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class BubbleAppDAO extends AccountOwnedTemplateDAO<BubbleApp> {

    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private AppMessageDAO messageDAO;
    @Autowired private AppDataDAO dataDAO;
    @Autowired private RuleEngineService ruleEngineService;

    @Override public Order getDefaultSortOrder() { return PRIORITY_ASC; }

    @Override public BubbleApp postUpdate(BubbleApp app, Object context) {
        ruleEngineService.flushCaches();
        return super.postUpdate(app, context);
    }

    @Override public void delete(String uuid) {
        final BubbleApp app = findByUuid(uuid);
        matcherDAO.delete(matcherDAO.findByApp(app.getUuid()));
        ruleDAO.delete(ruleDAO.findByApp(app.getUuid()));
        messageDAO.delete(messageDAO.findByApp(app.getUuid()));
        dataDAO.deleteApp(uuid);
        super.delete(uuid);
        ruleEngineService.flushCaches();
    }

    public BubbleApp findByAccountAndTemplateApp(String accountUuid, String templateAppUuid) {
        return findByUniqueFields("account", accountUuid, "templateApp", templateAppUuid);
    }

    public BubbleApp findByAccountAndName(String accountUuid, String name) {
        return findByUniqueFields("account", accountUuid, "name", name);
    }

}
