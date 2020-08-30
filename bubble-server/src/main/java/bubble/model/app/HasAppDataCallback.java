package bubble.model.app;

import bubble.model.account.Account;
import bubble.server.BubbleConfiguration;

import java.util.function.Function;

public interface HasAppDataCallback {

    void prime(Account account, BubbleApp app, BubbleConfiguration configuration);

    Function<AppData, AppData> createCallback(Account account, BubbleApp app, BubbleConfiguration configuration);

}
