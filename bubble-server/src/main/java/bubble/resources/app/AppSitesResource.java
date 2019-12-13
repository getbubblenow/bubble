package bubble.resources.app;

import bubble.dao.app.AppSiteDAO;
import bubble.model.account.Account;
import bubble.model.app.AppSite;
import bubble.model.app.BubbleApp;
import bubble.resources.account.AccountOwnedTemplateResource;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import java.util.List;

import static bubble.ApiConstants.EP_DATA;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class AppSitesResource extends AccountOwnedTemplateResource<AppSite, AppSiteDAO> {

    private BubbleApp app;

    public AppSitesResource(Account account, BubbleApp app) {
        super(account);
        this.app = app;
    }

    @Override protected List<AppSite> list(ContainerRequest ctx) {
        return getDao().findByAccountAndApp(getAccountUuid(ctx), app.getUuid());
    }

    @Override protected AppSite find(ContainerRequest ctx, String id) {
        return getDao().findByAccountAndAppAndId(getAccountUuid(ctx), app.getUuid(), id);
    }

    @Override protected AppSite setReferences(ContainerRequest ctx, Account caller, AppSite appSite) {
        appSite.setApp(app.getUuid());
        return super.setReferences(ctx, caller, appSite);
    }

    @Path("/{id}"+EP_DATA)
    public AppSiteDataResource getSiteData(@Context ContainerRequest ctx,
                                           @PathParam("id") String id) {
        final AppSite site = find(ctx, id);
        if (site == null) throw notFoundEx(id);
        return configuration.subResource(AppSiteDataResource.class, getAccount(account, ctx), app, site);
    }

}
