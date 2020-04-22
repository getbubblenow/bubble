/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources;

import bubble.model.account.Account;
import bubble.service.StandardSearchService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;
import static org.cobbzilla.wizard.resources.ResourceUtil.userPrincipal;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(SEARCH_ENDPOINT)
@Service @Slf4j
public class SearchResource {

    @Autowired private StandardSearchService searchService;

    @GET @Path("/{type}")
    public Response search(@Context Request req,
                           @Context ContainerRequest ctx,
                           @PathParam("type") String type,
                           @QueryParam(Q_META) Boolean meta,
                           @QueryParam(Q_NOCACHE) Boolean nocache,
                           @QueryParam(Q_FILTER) String filter,
                           @QueryParam(Q_PAGE) Integer page,
                           @QueryParam(Q_SIZE) Integer size,
                           @QueryParam(Q_SORT) String sort) {
        return search(req, ctx, type, meta, nocache, filter, page, size, sort, null);
    }

    @POST @Path("/{type}")
    public Response search(@Context Request req,
                           @Context ContainerRequest ctx,
                           @PathParam("type") String type,
                           @QueryParam(Q_META) Boolean meta,
                           @QueryParam(Q_NOCACHE) Boolean nocache,
                           @QueryParam(Q_FILTER) String filter,
                           @QueryParam(Q_PAGE) Integer page,
                           @QueryParam(Q_SIZE) Integer size,
                           @QueryParam(Q_SORT) String sort,
                           SearchQuery searchQuery) {

        final Account caller = userPrincipal(ctx);
        final String remoteHost = getRemoteHost(req);
        final String lang = normalizeLangHeader(req);
        return ok(searchService.search(type, meta, nocache, filter, page, size, sort, searchQuery, caller, req.getRequestURI(), remoteHost, lang));
    }

}
