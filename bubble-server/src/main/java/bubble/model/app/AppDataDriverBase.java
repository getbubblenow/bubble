package bubble.model.app;

import bubble.dao.app.AppDataDAO;
import bubble.model.account.Account;
import bubble.model.device.Device;
import bubble.service.SearchService;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.cobbzilla.wizard.model.search.SearchSort;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.wizard.model.search.SortOrder.ASC;

public abstract class AppDataDriverBase implements AppDataDriver {

    public static final SearchSort CASE_INSENSITIVE_KEY_SORT_ASC = new SearchSort("key", ASC, "lower");
    public static final SearchSort CTIME_SORT_ASC = new SearchSort("ctime");

    public static final SearchSort DEFAULT_SORT = CASE_INSENSITIVE_KEY_SORT_ASC;

    @Autowired protected AppDataDAO dataDAO;
    @Autowired protected SearchService searchService;
    @Autowired protected RedisService redis;

    @Override public SearchResults query(Account caller, Device device, BubbleApp app, AppSite site, AppDataView view, SearchQuery query) {
        query.setBound("app", app.getUuid());
        if (site != null) query.setBound("site", site.getUuid());
        if (!query.hasSorts()) query.addSort(DEFAULT_SORT);
        return searchService.search(false, caller, dataDAO, query);
    }

}
