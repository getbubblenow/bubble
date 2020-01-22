package bubble.rule;

import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.device.Device;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractAppRuleDriver implements AppRuleDriver {

    @Autowired protected BubbleConfiguration configuration;

    @Getter @Setter private AppRuleDriver next;

    protected JsonNode config;
    protected JsonNode userConfig;
    protected AppMatcher matcher;
    protected AppRule rule;
    protected Account account;
    protected Device device;

    @Override public void init(JsonNode config,
                               JsonNode userConfig,
                               AppRule rule,
                               AppMatcher matcher,
                               Account account,
                               Device device) {
        this.config = config;
        this.userConfig = userConfig;
        this.matcher = matcher;
        this.rule = rule;
        this.account = account;
        this.device = device;
    }

}
