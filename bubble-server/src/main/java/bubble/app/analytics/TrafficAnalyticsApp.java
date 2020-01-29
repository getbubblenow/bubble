package bubble.app.analytics;

import bubble.model.account.Account;
import bubble.model.app.*;
import bubble.model.device.Device;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.search.SearchBoundComparison;
import org.cobbzilla.wizard.model.search.SearchQuery;

import java.util.ArrayList;
import java.util.List;

import static bubble.rule.analytics.TrafficAnalytics.FQDN_SEP;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.string.StringUtil.PCT;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH;
import static org.cobbzilla.wizard.model.search.SearchBoundComparison.Constants.ILIKE_SEP;
import static org.cobbzilla.wizard.model.search.SearchField.OP_SEP;

public class TrafficAnalyticsApp extends AppDataDriverBase {

    public static final String VIEW_last_24_hours = "last_24_hours";
    public static final String VIEW_last_7_days = "last_7_days";
    public static final String VIEW_last_30_days = "last_30_days";

    @Override public SearchResults query(Account caller, Device device, BubbleApp app, AppSite site, AppDataView view, SearchQuery query) {
        query = query.setBound("key", getBound(view));
        return processResults(searchService.search(false, caller, dataDAO, query));
    }

    private SearchResults processResults(SearchResults searchResults) {
        final List<TrafficAnalyticsData> data = new ArrayList<>();
        if (searchResults.hasResults()) {
            for (AppData result : (List<AppData>) searchResults.getResults()) {
                data.add(new TrafficAnalyticsData(result));
            }
        }
        return searchResults.setResults(data);
    }

    private String getBound(AppDataView view) {
        final StringBuilder b;
        final long now = now();
        final int limit;
        switch (view.getName()) {
            default:
            case VIEW_last_24_hours: limit = 24; break;
            case VIEW_last_7_days:   limit = 24 * 7; break;
            case VIEW_last_30_days:  limit = 24 * 30; break;
        }

        b = new StringBuilder();
        for (int i = 0; i< limit; i++) {
            if (b.length() > 0) b.append(ILIKE_SEP);
            b.append(PCT + FQDN_SEP).append(DATE_FORMAT_YYYY_MM_DD_HH.print(now - HOURS.toMillis(i))).append("-").append(PCT);
        }

        return SearchBoundComparison.like_any.name() + OP_SEP + b.toString();
    }

}
