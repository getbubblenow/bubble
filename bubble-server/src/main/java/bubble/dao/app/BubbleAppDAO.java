package bubble.dao.app;

import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BubbleAppDAO extends AccountOwnedTemplateDAO<BubbleApp> {

    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private AppRuleDAO ruleDAO;

    public List<BubbleApp> findByAccountAndEnabledAndTemplate(String account, Boolean enabled, Boolean template) {
        if (enabled != null) {
            if (template != null) {
                return findByFields("account", account, "enabled", enabled, "template", template);
            } else {
                return findByFields("account", account, "enabled", enabled);
            }
        } else if (template != null) {
            return findByFields("account", account, "template", template);
        }
        return findByField("account", account);
    }

    @Override public void delete(String uuid) {
        final BubbleApp app = findByUuid(uuid);
        for (AppMatcher m : matcherDAO.findByApp(app.getUuid())) {
            matcherDAO.delete(m.getUuid());
        }
        for (AppRule r : ruleDAO.findByApp(app.getUuid())) {
            ruleDAO.delete(r.getUuid());
        }
        super.delete(uuid);
    }

}
