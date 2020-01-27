package bubble.resources.app;

import bubble.dao.app.AppMessageDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMessage;
import bubble.model.app.BubbleApp;
import bubble.resources.account.AccountOwnedResource;
import org.glassfish.jersey.server.ContainerRequest;

import java.util.List;

public class AppMessagesResource extends AccountOwnedResource<AppMessage, AppMessageDAO> {

    private BubbleApp app;

    public AppMessagesResource(Account account, BubbleApp app) {
        super(account);
        this.app = app;
    }

    @Override protected List<AppMessage> list(ContainerRequest ctx) {
        return getDao().findByAccountAndApp(getAccountUuid(ctx), app.getUuid());
    }

    @Override protected AppMessage find(ContainerRequest ctx, String locale) {
        return getDao().findByAccountAndAppAndLocale(getAccountUuid(ctx), app.getUuid(), locale);
    }

    @Override protected AppMessage findAlternate(ContainerRequest ctx, String locale) {
        final AppMessage defaultMessages = getDao().findByAccountAndAppAndDefaultLocale(getAccountUuid(ctx), app.getUuid());
        return defaultMessages != null ? defaultMessages : getDao().findByAccountAndAppAndHighestPriority(getAccountUuid(ctx), app.getUuid());
    }

    @Override protected AppMessage findAlternateForCreate(ContainerRequest ctx, AppMessage request) { return null; }
    @Override protected AppMessage findAlternateForUpdate(ContainerRequest ctx, String id) { return null; }
    @Override protected AppMessage findAlternateForDelete(ContainerRequest ctx, String id) { return null; }

    @Override protected AppMessage setReferences(ContainerRequest ctx, Account caller, AppMessage appMessage) {
        appMessage.setApp(app.getUuid());
        return super.setReferences(ctx, caller, appMessage);
    }

}
