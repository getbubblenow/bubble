package bubble.dao.app;

import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.app.BubbleApp;
import bubble.service.stream.RuleEngineService;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Repository
public class BubbleAppDAO extends AccountOwnedTemplateDAO<BubbleApp> {

    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private AppMessageDAO messageDAO;
    @Autowired private AppDataDAO dataDAO;
    @Autowired private RuleEngineService ruleEngineService;

    @Override public Order getDefaultSortOrder() { return NAME_ASC; }

    @Override public Object preUpdate(BubbleApp app) {
        final BubbleApp existing = findByUuid(app.getUuid());
        if (existing == null) return die("preUpdate: app does not exist: "+app.getUuid());
        if (existing.enabled() != app.enabled()) app.setFlushRuleCache(true);
        return super.preUpdate(app);
    }

    @Override public BubbleApp postUpdate(BubbleApp app, Object context) {
        if (app.isFlushRuleCache()) ruleEngineService.flushRuleCache();
        return super.postUpdate(app, context);
    }

    @Override public void delete(String uuid) {
        final BubbleApp app = findByUuid(uuid);
        matcherDAO.delete(matcherDAO.findByApp(app.getUuid()));
        ruleDAO.delete(ruleDAO.findByApp(app.getUuid()));
        messageDAO.delete(messageDAO.findByApp(app.getUuid()));
        dataDAO.deleteApp(uuid);
        super.delete(uuid);
        ruleEngineService.flushRuleCache();
    }

    public BubbleApp findByAccountAndTemplateApp(String accountUuid, String templateAppUuid) {
        return findByUniqueFields("account", accountUuid, "templateApp", templateAppUuid);
    }

}
