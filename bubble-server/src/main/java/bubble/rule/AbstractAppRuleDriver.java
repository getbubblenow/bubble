package bubble.rule;

import bubble.dao.app.AppDataDAO;
import bubble.dao.app.AppSiteDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.device.Device;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.system.Bytes;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractAppRuleDriver implements AppRuleDriver {

    public static final int RESPONSE_BUFSIZ = (int) (64 * Bytes.KB);

    @Autowired protected BubbleConfiguration configuration;
    @Autowired protected AppDataDAO appDataDAO;
    @Autowired protected AppSiteDAO appSiteDAO;

    @Getter @Setter private AppRuleDriver next;

    protected JsonNode config;
    protected JsonNode userConfig;
    protected AppMatcher matcher;
    protected AppRule rule;
    protected Account account;
    protected Device device;

    public Handlebars getHandlebars () { return configuration.getHandlebars(); }

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
