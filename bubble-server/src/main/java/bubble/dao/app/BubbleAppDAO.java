package bubble.dao.app;

import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.app.BubbleApp;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BubbleAppDAO extends AccountOwnedTemplateDAO<BubbleApp> {

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

}
