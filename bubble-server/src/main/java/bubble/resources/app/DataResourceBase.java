package bubble.resources.app;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.*;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppSite;
import bubble.model.app.BubbleApp;
import bubble.model.device.Device;
import bubble.resources.account.AccountOwnedTemplateResource;
import bubble.server.BubbleConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;

import static bubble.ApiConstants.EP_DISABLE;
import static bubble.ApiConstants.EP_ENABLE;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public abstract class DataResourceBase extends AccountOwnedTemplateResource<AppData, AppDataDAO> {

    private static final String[] BASIS_FIELDS = {"app", "site"};

    @Autowired protected BubbleConfiguration configuration;
    @Autowired protected AccountDAO accountDAO;
    @Autowired protected DeviceDAO deviceDAO;
    @Autowired protected BubbleAppDAO appDAO;
    @Autowired protected AppDataDAO dataDAO;
    @Autowired protected AppRuleDAO ruleDAO;
    @Autowired protected AppMatcherDAO matcherDAO;
    @Autowired protected AppSiteDAO siteDAO;

    protected AppData basis;

    public DataResourceBase (Account account, AppData basis) {
        super(account);
        this.basis = basis;
    }

    @Override protected List<AppData> list(ContainerRequest ctx) { return getDao().findByExample(account, basis); }

    @Override protected AppData find(ContainerRequest ctx, String key) {
        final List<AppData> found = getDao().findByExample(account, basis, key);
        if (found.isEmpty()) return null;
        if (found.size() == 1) return found.get(0);
        log.warn("find: multiple matches ("+found.size()+") for "+ key +": returning null");
        return null;
    }

    @POST @Path("/{id}"+EP_ENABLE)
    public Response enable(@Context ContainerRequest ctx,
                           @PathParam("id") String key) {
        if (isReadOnly(ctx)) return forbidden();
        final List<AppData> found = getDao().findByExample(account, basis, key);
        for (AppData d : found) {
            getDao().update(d.setEnabled(true));
        }
        return ok(found);
    }

    @POST @Path("/{id}"+EP_DISABLE)
    public Response disable(@Context ContainerRequest ctx,
                            @PathParam("id") String key) {
        if (isReadOnly(ctx)) return forbidden();
        final List<AppData> found = getDao().findByExample(account, basis, key);
        for (AppData d : found) {
            getDao().update(d.setEnabled(false));
        }
        return ok(found);
    }

    @Override protected AppData setReferences(ContainerRequest ctx, Account caller, AppData request) {

        copy(request, basis, BASIS_FIELDS);

        final BubbleApp app = appDAO.findByAccountAndId(caller.getUuid(), request.getApp());
        if (app == null) throw notFoundEx(request.getApp());
        request.setApp(app.getUuid());

        final Device device = deviceDAO.findByAccountAndId(caller.getUuid(), request.getDevice());
        if (device == null) throw notFoundEx(request.getDevice());
        request.setDevice(device.getUuid());

        final AppSite site = siteDAO.findByAccountAndId(caller.getUuid(), request.getSite());
        if (site == null) throw notFoundEx(request.getSite());
        request.setSite(site.getUuid());

        final AppMatcher matcher = matcherDAO.findByAccountAndId(caller.getUuid(), request.getMatcher());
        if (matcher == null) throw notFoundEx(request.getMatcher());
        request.setMatcher(matcher.getUuid());

        final AppData found = dataDAO.findByAppAndSiteAndKey(app.getUuid(), request.getSite(), request.getKey());

        if (found != null) {
            // OK if app.account matches getAccountUuid, NOT OK if mismatch
            return (AppData) found.update(request);

        } else {
            return request
                    .setAccount(app.getAccount())
                    .setApp(app.getUuid())
                    .setSite(site.getUuid())
                    .setMatcher(matcher.getUuid());
        }
    }

    @Override protected Object daoCreate(AppData appData) {
        return appData.hasUuid() ? getDao().update(appData) : super.daoCreate(appData);
    }

}
