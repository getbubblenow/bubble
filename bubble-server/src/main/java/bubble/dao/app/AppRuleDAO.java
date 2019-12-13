package bubble.dao.app;

import bubble.model.app.AppRule;
import org.springframework.stereotype.Repository;

@Repository public class AppRuleDAO extends AppTemplateEntityDAO<AppRule> {

    @Override public AppRule postUpdate(AppRule entity, Object context) {
        // todo: update entities based on this template if account has updates enabled
        return super.postUpdate(entity, context);
    }
}
