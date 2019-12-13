package bubble.resources.app;

import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import bubble.resources.driver.DriversResourceBase;

public class AppDriversResource extends DriversResourceBase {

    private BubbleApp app;

    public AppDriversResource(Account account, BubbleApp app) {
        super(account);
        this.app = app;
    }

}
