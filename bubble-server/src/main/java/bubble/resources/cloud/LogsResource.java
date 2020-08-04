/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.model.account.Account;
import bubble.service.boot.SelfNodeService;
import lombok.NonNull;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nullable;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static bubble.ApiConstants.*;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.forbiddenEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class LogsResource {

    @Autowired private SelfNodeService selfNodeService;

    private Account account;

    public LogsResource(@NonNull final Account account) {
        this.account = account;
    }

    @GET @Path(EP_STATUS)
    @NonNull public Response getLoggingStatus(@NonNull @Context final ContainerRequest ctx) {
        final var flag = new HashMap<String, Object>(2);
        flag.put("flag", selfNodeService.getLogFlag());
        flag.put("expireAt", selfNodeService.getLogFlagExpirationTime().orElse(null));
        return ok(flag);
    }

    @POST @Path(EP_START)
    @NonNull public Response startLogging(@NonNull @Context final ContainerRequest ctx,
                                          @Nullable @QueryParam("ttlDays") final Byte ttlDays) {
        return setLogFlag(true, Optional.ofNullable(ttlDays));
    }

    @POST @Path(EP_STOP)
    @NonNull public Response stopLogging(@NonNull @Context final ContainerRequest ctx) {
        return setLogFlag(false, Optional.empty());
    }

    @NonNull private Response setLogFlag(final boolean b, @NonNull final Optional<Byte> ttlInDays) {
        if (!account.admin()) throw forbiddenEx(); // caller must be admin
        selfNodeService.setLogFlag(b, ttlInDays.map(days -> (int) TimeUnit.DAYS.toSeconds(days)));
        return ok();
    }
}
