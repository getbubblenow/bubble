/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources;

import bubble.model.account.Account;
import bubble.service.StandardSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;
import static org.cobbzilla.wizard.resources.ResourceUtil.userPrincipal;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(SEARCH_ENDPOINT)
@Service @Slf4j
public class SearchResource {

    @Autowired private StandardSearchService searchService;

    @GET @Path("/{type}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_SEARCH,
            summary="Search model objects",
            description="Search model objects",
            parameters={
                    @Parameter(name="type", description="entity type to search", required=true),
                    @Parameter(name=Q_META, description="meta flag. if true, do not search, instead return metadata about how searches can be performed, which fields can be filtered and so on"),
                    @Parameter(name=Q_NOCACHE, description="nocache flag. if true, skip the cache and always run a real search"),
                    @Parameter(name=Q_FILTER, description="a filter string. if present, only entities matching this filter will be returned"),
                    @Parameter(name=Q_PAGE, description="page number. default is page 1"),
                    @Parameter(name=Q_SIZE, description="page size. default is 10, max is 50"),
                    @Parameter(name=Q_SORT, description="sort field. prefix with + or - to indicate ascending/descending")
            },
            responses=@ApiResponse(responseCode=SC_OK, description="a SearchResults object, or if meta was true then a SqlViewField[] array")
    )
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
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_SEARCH,
            summary="Search model objects",
            description="Search model objects",
            parameters={
                    @Parameter(name="type", description="entity type to search", required=true),
                    @Parameter(name=Q_META, description="meta flag. if true, do not search, instead return metadata about how searches can be performed, which fields can be filtered and so on"),
                    @Parameter(name=Q_NOCACHE, description="nocache flag. if true, skip the cache and always run a real search"),
                    @Parameter(name=Q_FILTER, description="a filter string. if present, only entities matching this filter will be returned"),
                    @Parameter(name=Q_PAGE, description="page number. default is page 1"),
                    @Parameter(name=Q_SIZE, description="page size. default is 10, max is 50"),
                    @Parameter(name=Q_SORT, description="sort field. prefix with + or - to indicate ascending/descending")
            },
            responses=@ApiResponse(responseCode=SC_OK, description="a SearchResults object, or if meta was true then a SqlViewField[] array")
    )
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
