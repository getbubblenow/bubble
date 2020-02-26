/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.resources;

import bubble.dao.account.AccountDAO;
import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
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
                } else if (id.equals(caller.getUuid()) || id.equals(caller.getName())) {
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
