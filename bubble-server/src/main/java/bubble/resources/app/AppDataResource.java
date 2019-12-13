package bubble.resources.app;

import bubble.model.account.Account;
import bubble.model.app.AppData;
import bubble.model.app.BubbleApp;

public class AppDataResource extends DataResourceBase {

    public AppDataResource(Account account, BubbleApp app) {
        super(account, new AppData().setAccount(account.getUuid()).setApp(app.getUuid()));
    }

}
