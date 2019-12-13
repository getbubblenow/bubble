package bubble.dao.app;

import bubble.model.app.AppSite;
import org.springframework.stereotype.Repository;

@Repository
public class AppSiteDAO extends AppTemplateEntityDAO<AppSite> {

    @Override public AppSite postCreate(AppSite entity, Object context) {
        // todo: update entities based on this template if account has updates enabled
        return super.postCreate(entity, context);
    }
}
