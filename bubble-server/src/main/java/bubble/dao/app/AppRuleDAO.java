package bubble.dao.app;

import bubble.model.app.AppRule;
import bubble.service.stream.RuleEngineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository public class AppRuleDAO extends AppTemplateEntityDAO<AppRule> {

    @Autowired private RuleEngineService ruleEngineService;

    @Override public AppRule postUpdate(AppRule entity, Object context) {

        ruleEngineService.flushCaches();

        // todo: update entities based on this template if account has updates enabled
        return super.postUpdate(entity, context);
    }

    @Override public void delete(String uuid) {
        super.delete(uuid);
        ruleEngineService.flushCaches();
    }
}
