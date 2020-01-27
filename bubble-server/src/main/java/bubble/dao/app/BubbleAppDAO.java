package bubble.dao.app;

import bubble.dao.account.AccountOwnedTemplateDAO;
import bubble.model.app.BubbleApp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class BubbleAppDAO extends AccountOwnedTemplateDAO<BubbleApp> {

    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private AppMessageDAO messageDAO;
    @Autowired private AppDataDAO dataDAO;

    @Override public void delete(String uuid) {
        final BubbleApp app = findByUuid(uuid);
        matcherDAO.delete(matcherDAO.findByApp(app.getUuid()));
        ruleDAO.delete(ruleDAO.findByApp(app.getUuid()));
        messageDAO.delete(messageDAO.findByApp(app.getUuid()));
        dataDAO.deleteApp(uuid);
        super.delete(uuid);
    }

}
