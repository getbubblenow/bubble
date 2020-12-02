/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources;

import bubble.model.HasBubbleTags;
import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.Getter;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.dao.DAO;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class TagsResource {

    private final HasBubbleTags taggable;

    @Autowired private BubbleConfiguration configuration;

    @Getter(lazy=true) private final DAO dao = configuration.getDaoForEntityClass(taggable.getClass());

    public TagsResource (HasBubbleTags taggable) {
        this.taggable = taggable;
    }

    @GET public Response list(@Context ContainerRequest ctx) { return ok(taggable.getTags()); }

    @POST @Path("/{name}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            summary="Set a tag",
            description="Set a tag",
            parameters=@Parameter(name="name", description="name of the tag"),
            responses=@ApiResponse(responseCode=SC_OK, description="a BubbleTags object representing the current list of tags")
    )
    public Response set(@Context ContainerRequest ctx,
                        @PathParam("name") String name,
                        NameAndValue nameAndValue) {

        final Account caller = userPrincipal(ctx);
        if (!caller.admin() && !caller.getUuid().equals(taggable.getAccount())) return forbidden();

        if (nameAndValue.hasName() && !nameAndValue.getName().equals(name)) return invalid("err.name.mismatch");
        if (!nameAndValue.hasValue()) return invalid("err.value.required");

        // relookup taggable
        final HasBubbleTags current = (HasBubbleTags) getDao().findByUuid(taggable.getUuid());
        if (current == null) return notFound(taggable.getUuid());

        current.setTag(name, nameAndValue.getValue());
        getDao().update(current);
//        return ok(current.getTags());
        return ok(((HasBubbleTags) getDao().findByUuid(taggable.getUuid())).getTags());
    }

}
