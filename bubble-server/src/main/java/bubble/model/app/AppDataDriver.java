package bubble.model.app;

import bubble.model.account.Account;
import bubble.model.device.Device;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.search.SearchQuery;

public interface AppDataDriver {

    SearchResults query(Account caller, Device device, BubbleApp app, AppSite site, AppDataView view, SearchQuery query);

}
