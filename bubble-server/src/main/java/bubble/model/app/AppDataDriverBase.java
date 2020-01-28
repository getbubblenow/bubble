package bubble.model.app;

import bubble.dao.app.AppDataDAO;
import bubble.model.account.Account;
import bubble.service.SearchService;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AppDataDriverBase implements AppDataDriver {

    @Autowired protected AppDataDAO dataDAO;
    @Autowired protected SearchService searchService;

    @Override public SearchResults query(Account caller, AppDataConfig dataConfig, AppDataView view, SearchQuery query) {
        return searchService.search(false, caller, dataDAO, query);
    }

}
