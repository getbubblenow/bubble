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
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Predicate;

import static bubble.ApiConstants.DEBUG_ENDPOINT;
import static bubble.cloud.auth.RenderedMessage.filteredInbox;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
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

        final Account account = configuration.isTestMode() ? optionalUserPrincipal(ctx) : userPrincipal(ctx);
        if (!configuration.isTestMode() && !account.admin()) return forbidden();

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

}
