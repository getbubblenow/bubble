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
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static bubble.ApiConstants.DEBUG_ENDPOINT;
import static bubble.cloud.auth.RenderedMessage.filteredInbox;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
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
    public Response springBeans(@Context ContainerRequest ctx,
                                @QueryParam("type") String type) {
        final ApplicationContext spring = configuration.getApplicationContext();
        if (type == null) {
            final String[] names = spring.getBeanDefinitionNames();
            Arrays.sort(names);
            return ok(names);
        }
        final Collection<String> beansWithAnnotation;
        switch (type.toLowerCase()) {
            case "dao":      return ok(new TreeSet<>(spring.getBeansWithAnnotation(Repository.class).keySet()));
            case "service":  return ok(new TreeSet<>(spring.getBeansWithAnnotation(Service.class).keySet().stream().filter(n -> !n.endsWith("Resource")).collect(Collectors.toSet())));
            case "resource": return ok(new TreeSet<>(spring.getBeansWithAnnotation(Service.class).keySet().stream().filter(n -> n.endsWith("Resource")).collect(Collectors.toSet())));
            default: return invalid(type);
        }
    }

}
