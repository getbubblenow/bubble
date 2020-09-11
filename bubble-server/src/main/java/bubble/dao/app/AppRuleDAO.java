/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.app;

import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.service.stream.AppPrimerService;
import bubble.service.stream.RuleEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository @Slf4j
public class AppRuleDAO extends AppTemplateEntityDAO<AppRule> {

    @Autowired private RuleEngineService ruleEngineService;
    @Autowired private BubbleAppDAO appDAO;
    @Autowired private AppPrimerService appPrimerService;

    @Override public Object preUpdate(AppRule rule) {
        final AppRule existing = findByUuid(rule.getUuid());
        rule.setPreviousConfigJson(existing.getConfigJson());
        return super.preUpdate(rule);
    }

    @Override public AppRule postUpdate(AppRule rule, Object context) {

        if (rule.configJsonChanged()) {
            final BubbleApp app = appDAO.findByUuid(rule.getApp());
            appPrimerService.prime(app);
        }
        ruleEngineService.flushCaches(false);

        // todo: update entities based on this template if account has updates enabled
        return super.postUpdate(rule, context);
    }

    @Override public void delete(String uuid) {
        final AppRule rule = findByUuid(uuid);
        if (rule != null) {
            getConfiguration().deleteDependencies(rule);
            super.delete(uuid);
            ruleEngineService.flushCaches();
        }
    }

}
