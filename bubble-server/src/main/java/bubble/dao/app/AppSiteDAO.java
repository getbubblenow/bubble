package bubble.dao.app;

import bubble.model.app.AppSite;
import bubble.service.stream.RuleEngineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Repository
public class AppSiteDAO extends AppTemplateEntityDAO<AppSite> {

    @Autowired private RuleEngineService ruleEngineService;

    @Override public AppSite postCreate(AppSite site, Object context) {
        // todo: update entities based on this template if account has updates enabled
        return super.postCreate(site, context);
    }

    @Override public Object preUpdate(AppSite site) {
        final AppSite existing = findByUuid(site.getUuid());
        if (existing == null) return die("preUpdate: AppSite not found: "+site.getUuid());
        if (existing.enabled() != site.enabled()) site.setFlushRuleCache(true);
        return super.preUpdate(site);
    }

    @Override public AppSite postUpdate(AppSite entity, Object context) {
        if (entity.isFlushRuleCache()) ruleEngineService.flushRuleCache();
        return super.postUpdate(entity, context);
    }

    @Override public void delete(String uuid) {
        super.delete(uuid);
        ruleEngineService.flushRuleCache();
    }
}
