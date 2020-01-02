package bubble.resources;

import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import bubble.service.cloud.GeoService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.wizard.dao.AbstractDAO;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.cobbzilla.wizard.model.search.SqlViewField;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.ApiConstants.*;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.daemon.ZillaRuntime.hashOf;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(SEARCH_ENDPOINT)
@Service @Slf4j
public class SearchResource {

    @Autowired private BubbleConfiguration configuration;
    @Autowired private GeoService geoService;

    public static final String Q_FILTER = "query";
    public static final String Q_META = "meta";
    public static final String Q_PAGE = "page";
    public static final String Q_SIZE = "size";
    public static final String Q_SORT = "sort";

    private Map<String, DAO> daoCache = new ConcurrentHashMap<>();

    @GET @Path("/{type}")
    public Response search(@Context Request req,
                           @Context ContainerRequest ctx,
                           @PathParam("type") String type,
                           @QueryParam(Q_META) Boolean meta,
                           @QueryParam(Q_FILTER) String filter,
                           @QueryParam(Q_PAGE) Integer page,
                           @QueryParam(Q_SIZE) Integer size,
                           @QueryParam(Q_SORT) String sort) {
        return search(req, ctx, type, meta, filter, page, size, sort, null);
    }

    private ExpirationMap<String, Object> _searchCache = new ExpirationMap<String, Object>()
            .setExpiration(MINUTES.toMillis(2))
            .setMaxExpiration(MINUTES.toMillis(2))
            .setCleanInterval(MINUTES.toMillis(5));

    @POST @Path("/{type}")
    public Response search(@Context Request req,
                           @Context ContainerRequest ctx,
                           @PathParam("type") String type,
                           @QueryParam(Q_META) Boolean meta,
                           @QueryParam(Q_FILTER) String filter,
                           @QueryParam(Q_PAGE) Integer page,
                           @QueryParam(Q_SIZE) Integer size,
                           @QueryParam(Q_SORT) String sort,
                           SearchQuery searchQuery) {

        final Account caller = userPrincipal(ctx);
        final String cacheKey = hashOf(caller.getUuid(), type, meta, filter, page, size, sort, searchQuery);
        return ok(_searchCache.computeIfAbsent(cacheKey, searchKey -> {
            final DAO dao = daoCache.computeIfAbsent(type, k -> getDao(type));
            if (meta != null) return ((AbstractDAO) dao).getSearchFields();

            final SearchQuery q = searchQuery != null ? searchQuery : new SearchQuery();
            if (!q.hasLocale()) {
                try {
                    q.setLocale(geoService.getFirstLocale(caller, getRemoteHost(req), normalizeLangHeader(req)));
                } catch (Exception e) {
                    log.warn("search: error setting locale, using default: "+configuration.getDefaultLocale()+": "+e);
                    q.setLocale(configuration.getDefaultLocale());
                }
            }
            q.setPageNumber(page != null ? page : 1);
            q.setPageSize(size != null ? Integer.min(size, MAX_SEARCH_PAGE) : Integer.min(q.getPageSize(), MAX_SEARCH_PAGE));
            if (sort != null) {
                final SortAndOrder s = new SortAndOrder(sort);
                q.setSortField(s.getSortField());
                q.setSortOrder(s.getSortOrder());
            }
            if (filter != null) q.setFilter(filter);

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

            final SearchResults results = dao.search(q);
            if (results.hasNextPage(q)) {
                results.setNextPage(req.getRequestURI()+"?"+Q_PAGE+"="+(q.getPageNumber()+1)+"&"+Q_SIZE+"="+q.getPageSize());
            }
            return results;
        }));
    }

    public DAO getDao(String type) {
        for (Class c : configuration.getEntityClasses()) {
            if (c.getSimpleName().equalsIgnoreCase(type)) {
                return configuration.getDaoForEntityClass(c);
            }
        }
        throw notFoundEx(type);
    }

    private static class SortAndOrder {
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
