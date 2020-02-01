package bubble.model.app.config;

import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface AppConfigDriver {

    String PARAM_ID = "id";

    Object getView(Account account, BubbleApp app, String view, Map<String, String> params);

    Object takeAppAction(Account account,
                         BubbleApp app,
                         String view,
                         String action,
                         Map<String, String> params,
                         JsonNode data);

    Object takeItemAction(Account account,
                          BubbleApp app,
                          String view,
                          String action,
                          String id,
                          Map<String, String> params,
                          JsonNode data);
}
