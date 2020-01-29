package bubble.resources.app;

import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.app.BubbleApp;
import org.cobbzilla.util.collection.SingletonList;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.cobbzilla.wizard.resources.ResourceUtil.forbidden;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;

public class AppDataResource extends DataResourceBase {

    public AppDataResource(Account account, BubbleApp app) {
        super(account, app, new AppData().setAccount(account.getUuid()).setApp(app.getUuid()));
    }

    public Response delete(@Context ContainerRequest ctx, String key) {
        final Account caller = checkEditable(ctx);
        // try by id first
        final String accountUuid = getAccountUuid(ctx);
        final AppData byUuid = getDao().findByAccountAndId(accountUuid, key);
        final List<AppData> found;
        if (byUuid != null) {
            found = new SingletonList<>(byUuid);
        } else {
            found = getDao().findByAccountAndAppAndAndKey(accountUuid, basis.getApp(), key);
        }
        for (AppData d : found) {
            if (!canDelete(ctx, caller, d)) return forbidden();
        }
        getDao().delete(found);
        return ok(found);
    }

}
