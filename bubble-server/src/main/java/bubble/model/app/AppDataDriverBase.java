package bubble.model.app;

import bubble.dao.app.AppDataDAO;
import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.service.SearchService;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AppDataDriverBase implements AppDataDriver {

    @Autowired protected AppDataDAO dataDAO;
    @Autowired protected SearchService searchService;

    @Override public SearchResults query(Account caller, Device device, BubbleApp app, AppSite site, AppDataView view, SearchQuery query) {
        query.setBound("app", app.getUuid());
        return searchService.search(false, caller, dataDAO, query);
    }

}
