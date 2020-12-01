/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources;

import bubble.dao.account.AccountDAO;
import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.entityconfig.EntityConfig;
import org.cobbzilla.wizard.resources.AbstractEntityConfigsResource;
import org.cobbzilla.wizard.server.config.StaticHttpConfiguration;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static bubble.ApiConstants.ENTITY_CONFIGS_ENDPOINT;
import static java.lang.Boolean.TRUE;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.string.StringUtil.packagePath;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.API_TAG_UTILITY;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Path(ENTITY_CONFIGS_ENDPOINT)
@Service @Slf4j
public class EntityConfigsResource extends AbstractEntityConfigsResource {

    @Autowired private AccountDAO accountDAO;
    @Getter(AccessLevel.PROTECTED) @Autowired private BubbleConfiguration configuration;

    @Getter private final AtomicBoolean allowPublic = new AtomicBoolean(false);

    @POST @Path("/set/{param}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_UTILITY},
            summary="Set a configuration parameter to `true`",
            description="Set a configuration parameter to `true`",
            parameters={@Parameter(name="param", description="the name of the parameter to set")},
            responses={
                    @ApiResponse(description="the value that was set",
                            content={@Content(mediaType=APPLICATION_JSON, examples={
                                    @ExampleObject(name="should always return true", value="true")
                            }
                            )})
            }
    )
    public Response setConfig (@Context ContainerRequest ctx,
                               @PathParam("param") String param) {
        return setConfig(ctx, param, TRUE.toString());
    }

    @POST @Path("/set/{param}/{value}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_UTILITY},
            summary="Set a configuration parameter to a value",
            description="Set a configuration parameter to a value",
            parameters={
                    @Parameter(name="param", description="the name of the parameter to set"),
                    @Parameter(name="value", description="the value to set the parameter to")
            },
            responses={
                    @ApiResponse(description="the value that was set",
                            content={@Content(mediaType=APPLICATION_JSON, examples={
                                    @ExampleObject(name="if the value was the String 'foo'", value="\"foo\"")
                            }
                            )})
            }
    )
    public Response setConfig (@Context ContainerRequest ctx,
                               @PathParam("param") String param,
                               @PathParam("value") String value) {
        if (!authorized(ctx)) return forbidden();
        final Account account = userPrincipal(ctx);
        if (!account.admin()) return forbidden();
        switch (param) {
            case "public": allowPublic.set(Boolean.parseBoolean(value)); break;
            default: return invalid("err.ec.param.invalid");
        }
        return ok(value);
    }

    @Override protected boolean authorized(ContainerRequest ctx) {
        if (!accountDAO.activated()) return true;
        final Account account = allowPublic.get() ? optionalUserPrincipal(ctx) : userPrincipal(ctx);
        return allowPublic.get() || (account != null && !account.suspended());
    }

    @Override protected File getLocalConfig(EntityConfig config) {

        final StaticHttpConfiguration staticAssets = getConfiguration().getStaticAssets();
        if (staticAssets == null || !staticAssets.hasLocalOverride()) return null;

        final File localOverride = staticAssets.getLocalOverride();
        try {
            if (abs(localOverride).endsWith("/bubble-server/src/main/resources/site")) {
                // we are in a development environment
                return new File(abs(localOverride.getParentFile())
                        + "/" + ENTITY_CONFIG_BASE
                        + "/" + packagePath(config.getClassName()) + ".json");
            } else {
                // staging/production: localOverride is the site dir
                return new File(abs(localOverride)
                        + "/" + ENTITY_CONFIG_BASE
                        + "/" + packagePath(config.getClassName()) + ".json");
            }
        } catch (Exception e) {
            final String msg = "error getting config for: " + config;
            log.error(msg);
            return die(msg, e);
        }
    }

}
