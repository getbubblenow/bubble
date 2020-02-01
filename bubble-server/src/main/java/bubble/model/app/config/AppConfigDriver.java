package bubble.model.app.config;

import bubble.model.account.Account;
import bubble.model.app.BubbleApp;

import java.util.Map;

public interface AppConfigDriver {

    String PARAM_ID = "id";

    Object getView(Account account, BubbleApp app, String view, Map<String, String> params);

}
