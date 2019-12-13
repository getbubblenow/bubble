package bubble.rule;

import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractAppRuleDriver implements AppRuleDriver {

    @Autowired protected BubbleConfiguration configuration;

    @Getter @Setter private AppRuleDriver next;
    @Getter @Setter private String sessionId;

    protected JsonNode config;
    protected JsonNode userConfig;
    protected AppMatcher matcher;
    protected AppRule rule;

    @Override public void init(JsonNode config, JsonNode userConfig, AppRule rule, AppMatcher matcher) {
        this.config = config;
        this.userConfig = userConfig;
        this.matcher = matcher;
        this.rule = rule;
    }

}
