/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources;

import bubble.cloud.CloudServiceType;
import bubble.cloud.auth.RenderedMessage;
import bubble.cloud.email.mock.MockMailSender;
import bubble.cloud.sms.mock.MockSmsDriver;
import bubble.model.account.Account;
import bubble.model.account.message.AccountAction;
import bubble.model.account.message.AccountMessageType;
import bubble.model.account.message.ActionTarget;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static bubble.ApiConstants.API_TAG_DEBUG;
import static bubble.ApiConstants.DEBUG_ENDPOINT;
import static bubble.cloud.auth.RenderedMessage.filteredInbox;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_SERVER_ERROR;
import static org.cobbzilla.util.json.JsonUtil.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path(DEBUG_ENDPOINT)
@Service @Slf4j
public class DebugResource {

    @Autowired private BubbleConfiguration configuration;

    @GET @Path("/inbox/{type}/{recipient}")
    @Operation(tags=API_TAG_DEBUG,
            summary="Read debug mailboxes",
            description="Read debug mailboxes. Must be admin. These are only used in testing.",
            responses=@ApiResponse(responseCode=SC_OK, description="a JSON array of RenderedMessage")
    )
    public Response inbox(@Context ContainerRequest ctx,
                          @PathParam("type") CloudServiceType type,
                          @PathParam("recipient") String recipient,
                          @QueryParam("type") AccountMessageType messageType,
                          @QueryParam("action") AccountAction action,
                          @QueryParam("target") ActionTarget target) {

        final Account account = configuration.testMode() ? optionalUserPrincipal(ctx) : userPrincipal(ctx);
        if (!configuration.testMode() && !account.admin()) return forbidden();

        final Map<String, ArrayList<RenderedMessage>> spool;
        switch (type) {
            case email: spool = MockMailSender.getSpool(); break;
            case sms: spool = MockSmsDriver.getSpool(); break;
            default: return invalid("err.type.invalid", "type was not valid: "+type, ""+type);
        }
        return ok(filteredInbox(spool, recipient, getPredicate(messageType, action, target)));
    }

    protected Predicate<RenderedMessage> getPredicate(AccountMessageType messageType, AccountAction action, ActionTarget target) {

        final Predicate<RenderedMessage> typePredicate = messageType != null
                ? m -> m.getMessageType() == messageType
                : null;

        final Predicate<RenderedMessage> actionPredicate = action != null
                ? m -> m.getAction() == action
                : null;

        final Predicate<RenderedMessage> targetPredicate = target != null
                ? m -> m.getTarget() == target
                : null;

        return m -> ((typePredicate == null || typePredicate.test(m))
                && (actionPredicate == null || actionPredicate.test(m))
                && (targetPredicate == null || targetPredicate.test(m))
        );
    }

    @GET @Path("/error")
    @Operation(tags=API_TAG_DEBUG,
            summary="Generate an error",
            description="Generate an error",
            responses=@ApiResponse(responseCode=SC_SERVER_ERROR, description="an error")
    )
    public Response testError(@Context ContainerRequest ctx,
                              @QueryParam("type") String type,
                              @QueryParam("message") String message) {
        if (!empty(type)) {
            if (!empty(message)) {
                return die((Exception) instantiate(forName(type), message));
            } else {
                return die((Exception) instantiate(type));
            }
        } else {
            return die("testing error catcher");
        }
    }

    @GET @Path("/beans")
    @Operation(tags=API_TAG_DEBUG,
            summary="List Spring beans",
            description="List Spring beans",
            responses=@ApiResponse(responseCode=SC_OK, description="array of bean names")
    )
    public Response springBeans(@Context ContainerRequest ctx,
                                @QueryParam("type") String type) {
        final ApplicationContext spring = configuration.getApplicationContext();
        if (type == null) {
            final String[] names = spring.getBeanDefinitionNames();
            Arrays.sort(names);
            return ok(names);
        }
        switch (type.toLowerCase()) {
            case "dao":      return ok(new TreeSet<>(spring.getBeansWithAnnotation(Repository.class).keySet()));
            case "service":  return ok(new TreeSet<>(spring.getBeansWithAnnotation(Service.class).keySet().stream().filter(n -> !n.endsWith("Resource")).collect(Collectors.toSet())));
            case "resource": return ok(new TreeSet<>(spring.getBeansWithAnnotation(Service.class).keySet().stream().filter(n -> n.endsWith("Resource")).collect(Collectors.toSet())));
            default: return invalid(type);
        }
    }

    @POST @Path("/echo")
    @Operation(tags=API_TAG_DEBUG,
            summary="Echo JSON to log",
            description="Echo JSON to log",
            responses=@ApiResponse(responseCode=SC_OK, description="some JSON")
    )
    public Response echoJsonInLog(@Context ContainerRequest ctx,
                                  @Valid @NonNull final JsonNode input,
                                  @QueryParam("respondWith") @Nullable final String respondWith) throws IOException {
        final var output = "ECHO: \n" + toJsonOrDie(input);
        log.info(output);

        if (empty(respondWith)) return ok();

        log.debug("Responding with value in path: " + respondWith);
        return ok(getNodeAsJava(findNode(input, respondWith), ""));
    }
}
