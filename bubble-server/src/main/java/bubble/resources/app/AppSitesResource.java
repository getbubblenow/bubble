/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.app;

import bubble.dao.app.AppSiteDAO;
import bubble.model.account.Account;
import bubble.model.app.AppSite;
import bubble.model.app.BubbleApp;
import bubble.model.app.config.AppDataDriver;
import bubble.model.app.config.AppDataView;
import bubble.model.device.Device;
import bubble.resources.account.AccountOwnedTemplateResource;
import bubble.service.device.DeviceService;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;

import static bubble.ApiConstants.*;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

public class AppSitesResource extends AccountOwnedTemplateResource<AppSite, AppSiteDAO> {

    private final BubbleApp app;

    @Autowired private DeviceService deviceService;

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

    @POST @Path("/{id}"+EP_ENABLE)
    public Response enable(@Context ContainerRequest ctx,
                           @PathParam("id") String id) {
        if (isReadOnly(ctx)) return forbidden();
        final AppSite found = getDao().findByAccountAndAppAndId(getAccountUuid(ctx), app.getUuid(), id);
        if (found == null) return notFound(id);
        return ok(getDao().update(found.setEnabled(true)));
    }

    @POST @Path("/{id}"+EP_DISABLE)
    public Response disable(@Context ContainerRequest ctx,
                            @PathParam("id") String id) {
        if (isReadOnly(ctx)) return forbidden();
        final AppSite found = getDao().findByAccountAndAppAndId(getAccountUuid(ctx), app.getUuid(), id);
        if (found == null) return notFound(id);
        return ok(getDao().update(found.setEnabled(false)));
    }

    @Path("/{id}"+EP_MATCHERS)
    public AppMatchersResource getSiteMatchers(@Context ContainerRequest ctx,
                                               @PathParam("id") String id) {
        final AppSite site = find(ctx, id);
        if (site == null) throw notFoundEx(id);
        return configuration.subResource(AppMatchersResource.class, getAccount(account, ctx), app, site);
    }

    @Path("/{id}"+EP_DATA)
    public AppSiteDataResource getSiteData(@Context ContainerRequest ctx,
                                           @PathParam("id") String id) {
        final AppSite site = find(ctx, id);
        if (site == null) throw notFoundEx(id);
        return configuration.subResource(AppSiteDataResource.class, getAccount(account, ctx), app, site);
    }

    @POST @Path("/{id}"+EP_VIEW+"/{view}")
    public Response search(@Context Request req,
                           @Context ContainerRequest ctx,
                           @PathParam("id") String id,
                           @PathParam("view") String viewName,
                           SearchQuery query) {

        final Account caller = userPrincipal(ctx);
        final AppSite site = getDao().findByAccountAndId(getAccountUuid(ctx), id);
        if (site == null) return notFound(id);
        if (!app.hasDataConfig()) return notFound(id);

        final AppDataView view = app.getDataConfig().getView(viewName);
        if (view == null) return notFound(viewName);

        final String remoteHost = getRemoteHost(req);
        final Device device = deviceService.findDeviceByIp(remoteHost);

        final AppDataDriver driver = app.getDataConfig().getDataDriver(configuration);
        return ok(driver.query(caller, device, app, site, view, query));
    }

}
