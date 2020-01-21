package bubble.resources.app;

import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.stereotype.Service;

import javax.ws.rs.Path;
import java.util.List;

import static bubble.ApiConstants.APPS_ENDPOINT;
import static org.cobbzilla.wizard.resources.ResourceUtil.userPrincipal;

@Path(APPS_ENDPOINT)
@Service @Slf4j
public class PublicAppsResource extends AppsResourceBase {

    public PublicAppsResource() { super(null); }

    @Override protected List<BubbleApp> list(ContainerRequest ctx) {
        return getDao().findPublicTemplates(getAccountUuid(ctx));
    }

    @Override protected BubbleApp find(ContainerRequest ctx, String id) {
        final BubbleApp found = super.find(ctx, id);
        if (found != null) {
            final Account caller = userPrincipal(ctx);
            if (caller.admin()) return found;
            if (found.enabled() && found.template()) return found;
        }
        return null;
    }

}
