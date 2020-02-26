/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources;

import bubble.model.HasBubbleTags;
import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.dao.DAO;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class TagsResource {

    private HasBubbleTags taggable;

    @Autowired private BubbleConfiguration configuration;

    @Getter(lazy=true) private final DAO dao = configuration.getDaoForEntityClass(taggable.getClass());

    public TagsResource (HasBubbleTags taggable) {
        this.taggable = taggable;
    }

    @GET public Response list(@Context ContainerRequest ctx) { return ok(taggable.getTags()); }

    @POST @Path("/{name}")
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
