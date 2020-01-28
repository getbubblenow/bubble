package bubble.model.app;

import bubble.model.account.Account;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.search.SearchQuery;

public interface AppDataDriver {

    SearchResults query(Account caller, AppSite site, AppDataConfig dataConfig, AppDataView view, SearchQuery query);

}
