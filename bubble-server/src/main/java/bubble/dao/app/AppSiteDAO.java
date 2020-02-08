package bubble.dao.app;

import bubble.model.app.AppSite;
import bubble.service.stream.RuleEngineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class AppSiteDAO extends AppTemplateEntityDAO<AppSite> {

    @Autowired private RuleEngineService ruleEngineService;

    @Override public AppSite postCreate(AppSite site, Object context) {
        // todo: update entities based on this template if account has updates enabled
        return super.postCreate(site, context);
    }

    @Override public AppSite postUpdate(AppSite entity, Object context) {
        ruleEngineService.flushCaches();
        return super.postUpdate(entity, context);
    }

    @Override public void delete(String uuid) {
        super.delete(uuid);
        ruleEngineService.flushCaches();
    }
}
