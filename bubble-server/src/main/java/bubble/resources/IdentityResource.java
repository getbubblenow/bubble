/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.model.Identifiable;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static bubble.ApiConstants.ID_ENDPOINT;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.API_TAG_UTILITY;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(ID_ENDPOINT)
@Service @Slf4j
public class IdentityResource {

    @Autowired private BubbleConfiguration configuration;

    @GET
    public Response identifyNothing(@Context Request req,
                                    @Context ContainerRequest ctx) { return ok_empty(); }

    @GET @Path("/{id}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_UTILITY},
            summary="Find what object(s) an ID belongs to. Useful when you have a UUID but don't know what kind of thing it refers to, if any.",
            description="Searches all model objects by ID. The id parameter is typically a UUID or name",
            parameters={@Parameter(name="id", description="an identifier (typically UUID or name) to search for")},
            responses={
                    @ApiResponse(description="a JSON object where the property names are entity types, and a property's corresponding value is the object of that type found with the given ID",
                            content={@Content(mediaType=APPLICATION_JSON, examples={
                                    @ExampleObject(name="usually a UUID only matches one object", value="{\"CloudService\": {\"uuid\": \"the-ID-you-searched-for\", \"other-cloud-service-fields\": \"would-be-shown\"}}"),
                                    @ExampleObject(name="a UUID for an Account also matches the AccountPolicy", value="{\"Account\": {\"uuid\": \"the-ID-you-searched-for\", \"other-account-fields\": \"would-be-shown\"}, \"AccountPolicy\": {\"uuid\": \"the-ID-you-searched-for\", \"other-policy-fields\": \"would-be-shown\"}}"),
                                    @ExampleObject(name="empty JSON object when no matches are found", value="{}")
                            }
                            )})
            }
    )
    public Response identify(@Context Request req,
                             @Context ContainerRequest ctx,
                             @PathParam("id") String id) {
        final Account caller = userPrincipal(ctx);
        final Map<String, Identifiable> entities = new HashMap<>();
        for (Class<? extends Identifiable> type : configuration.getEntityClasses()) {
            final DAO dao = configuration.getDaoForEntityClass(type);
            final Identifiable found;
            if (dao instanceof AccountOwnedEntityDAO) {
                // find things we own with the given id
                found = ((AccountOwnedEntityDAO) dao).findByAccountAndId(caller.getUuid(), id);

            } else if (dao instanceof AccountDAO) {
                if (caller.admin()) {
                    // only admin can find any user
                    found = ((AccountDAO) dao).findById(id);
                } else if (id.equals(caller.getUuid()) || id.equals(caller.getEmail())) {
                    // other callers can find themselves
                    found = caller;
                } else {
                    found = null;
                }

            } else if (caller.admin()) {
                // admins can find anything anywhere, regardless of who owns it
                found = dao.findByUuid(id);

            } else {
                // everything else is not found
                found = null;
            }
            if (found != null) entities.put(type.getName(), found);
        }
        return ok(entities);
    }

}
