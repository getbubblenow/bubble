package bubble.service;

import bubble.ApiConstants;
import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import bubble.service.cloud.GeoService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationEvictionPolicy;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.wizard.dao.AbstractDAO;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.cobbzilla.wizard.model.search.SqlViewField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.ApiConstants.MAX_SEARCH_PAGE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Service @Slf4j
public class SearchService {

    @Autowired private BubbleConfiguration configuration;
    @Autowired private GeoService geoService;

    private Map<String, DAO> daoCache = new ConcurrentHashMap<>();

    private ExpirationMap<String, Object> _searchCache = new ExpirationMap<String, Object>()
            .setExpiration(MINUTES.toMillis(2))
            .setMaxExpiration(MINUTES.toMillis(2))
            .setCleanInterval(MINUTES.toMillis(5))
            .setEvictionPolicy(ExpirationEvictionPolicy.atime);

    public static DAO getDao(BubbleConfiguration configuration, String type) {
        for (Class c : configuration.getEntityClasses()) {
            if (c.getName().equalsIgnoreCase(type) || c.getSimpleName().equalsIgnoreCase(type)) {
                return configuration.getDaoForEntityClass(c);
            }
        }
        throw notFoundEx(type);
    }

    public Object search(String type,
                         Boolean meta,
                         Boolean nocache,
                         String filter,
                         Integer page,
                         Integer size,
                         String sort,
                         SearchQuery searchQuery,
                         Account caller,
                         String requestURI,
                         String remoteHost,
                         String lang) {

        final DAO dao = daoCache.computeIfAbsent(type, k -> SearchService.getDao(configuration, type));
        if (meta != null) return ((AbstractDAO) dao).getSearchFields();

        final SearchQuery q = searchQuery != null ? searchQuery : new SearchQuery();
        if (!q.hasLocale()) {
            try {
                q.setLocale(geoService.getFirstLocale(caller, remoteHost, lang));
            } catch (Exception e) {
                log.warn("search: error setting locale, using default: "+configuration.getDefaultLocale()+": "+e);
                q.setLocale(configuration.getDefaultLocale());
            }
        }
        q.setPageNumber(page != null ? page : 1);
        q.setPageSize(size != null ? Integer.min(size, MAX_SEARCH_PAGE) : Integer.min(q.getPageSize(), MAX_SEARCH_PAGE));
        if (sort != null) {
            final SearchService.SortAndOrder s = new SearchService.SortAndOrder(sort);
            q.setSortField(s.getSortField());
            q.setSortOrder(s.getSortOrder());
        }
        if (filter != null) q.setFilter(filter);

        final SearchResults results;
        if (nocache != null && nocache) {
            results = _search(dao, q, caller);
        } else {
            final String cacheKey = hashOf(caller.getUuid(), type, q);
            results = (SearchResults) _searchCache.computeIfAbsent(cacheKey, searchKey -> _search(dao, q, caller));
        }
        if (results.hasNextPage(q)) {
            results.setNextPage(requestURI +"?"+ ApiConstants.Q_PAGE+"="+(q.getPageNumber()+1)+"&"+ ApiConstants.Q_SIZE+"="+q.getPageSize());
        }
        return results;
    }

    public static SearchResults _search(DAO dao,
                                        SearchQuery q,
                                        Account caller) {
        if (!caller.admin()) {
            final Class entityClass = dao.getEntityClass();
            if (entityClass.equals(Account.class)) {
                // non-admins can only look up themselves
                q.setBound("uuid", caller.getUuid());
            } else {
                final SqlViewField accountField = ((AbstractDAO) dao).findSearchField(entityClass, "account");
                if (accountField != null) {
                    q.setBound("account", caller.getUuid());
                } else {
                    // no results, non-admin cannot search for things that do not have an account
                    return new SearchResults<>().setError("cannot search "+ entityClass.getName());
                }
            }
        }
        return dao.search(q);
    }

    public static class SortAndOrder {
        @Getter private final String sortField;
        @Getter private final SearchQuery.SortOrder sortOrder;
        public SortAndOrder(String sort) {
            if (sort.startsWith("+") || sort.startsWith(" ")) {
                sortField = sort.substring(1).trim();
                sortOrder = SearchQuery.SortOrder.ASC;
            } else if (sort.startsWith("-")) {
                sortField = sort.substring(1).trim();
                sortOrder = SearchQuery.SortOrder.DESC;
            } else if (sort.endsWith("+") || sort.endsWith(" ")) {
                sortField = sort.substring(0, sort.length()-1).trim();
                sortOrder = SearchQuery.SortOrder.ASC;
            } else if (sort.endsWith("-")) {
                sortField = sort.substring(0, sort.length()-1).trim();
                sortOrder = SearchQuery.SortOrder.DESC;
            } else {
                sortField = sort.trim();
                sortOrder = SearchQuery.SortOrder.ASC;
            }
        }
    }
}
