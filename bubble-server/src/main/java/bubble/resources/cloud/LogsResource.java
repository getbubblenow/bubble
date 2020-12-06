/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.cloud;

import bubble.model.account.Account;
import bubble.service.boot.SelfNodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.NonNull;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Optional;

import static bubble.ApiConstants.*;
import static bubble.service.boot.StandardSelfNodeService.MAX_LOG_TTL_DAYS;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.API_TAG_UTILITY;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class LogsResource {

    public static final String K_FLAG = "flag";
    public static final String K_EXPIRE_AT = "expireAt";

    @Autowired private SelfNodeService selfNodeService;

    private final Account account;

    public LogsResource(@NonNull final Account account) { this.account = account; }

    @GET @Path(EP_STATUS)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_BUBBLE_INFO, API_TAG_UTILITY},
            summary="Get logging status",
            description="Get logging status. Must be admin. Returns a JSON object with keys `"+K_FLAG+"` (boolean, indicates if logging is enabled) and `"+K_EXPIRE_AT+"` (epoch time in milliseconds when logging will automatically be turned off)",
            responses=@ApiResponse(responseCode=SC_OK, description="true if logs enabled, false otherwise")
    )
    @NonNull public Response getLoggingStatus(@NonNull @Context final ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) throw forbiddenEx();

        final var flag = new HashMap<String, Object>(2);
        flag.put(K_FLAG, selfNodeService.getLogFlag());
        flag.put(K_EXPIRE_AT, selfNodeService.getLogFlagExpirationTime().orElse(null));
        return ok(flag);
    }

    @POST @Path(EP_START)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_UTILITY,
            summary="Enable logging",
            description="Enable logging. Must be admin.",
            parameters=@Parameter(name="ttlDays", description="Logging will be disabled after this many days have passed. Max is "+MAX_LOG_TTL_DAYS),
            responses=@ApiResponse(responseCode=SC_OK, description="empty response indicates success")
    )
    @NonNull public Response startLogging(@NonNull @Context final ContainerRequest ctx,
                                          @QueryParam("ttlDays") final Byte ttlDays) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) throw forbiddenEx();
        return setLogFlag(true, Optional.ofNullable(ttlDays));
    }

    @POST @Path(EP_STOP)
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags=API_TAG_UTILITY,
            summary="Disable logging",
            description="Disable logging. Must be admin.",
            responses=@ApiResponse(responseCode=SC_OK, description="empty response indicates success")
    )
    @NonNull public Response stopLogging(@NonNull @Context final ContainerRequest ctx) {
        final Account caller = userPrincipal(ctx);
        if (!caller.admin()) throw forbiddenEx();
        return setLogFlag(false, Optional.empty());
    }

    @NonNull private Response setLogFlag(final boolean b, @NonNull final Optional<Byte> ttlInDays) {
        if (!account.admin()) throw forbiddenEx(); // caller must be admin
        selfNodeService.setLogFlag(b, ttlInDays.map(days -> (int) DAYS.toSeconds(days)));
        return ok();
    }
}
