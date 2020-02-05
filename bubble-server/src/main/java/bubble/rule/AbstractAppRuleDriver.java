package bubble.rule;

import bubble.dao.app.AppDataDAO;
import bubble.dao.app.AppSiteDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.device.Device;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.http.HttpContentTypeAndCharset;
import org.cobbzilla.util.system.Bytes;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.util.http.HttpContentTypes.TEXT_HTML;

public abstract class AbstractAppRuleDriver implements AppRuleDriver {

    public static final int RESPONSE_BUFSIZ = (int) (64 * Bytes.KB);

    public static final String CTX_JS_PREFIX = "JS_PREFIX";
    public static final String CTX_BUBBLE_REQUEST_ID = "BUBBLE_REQUEST_ID";
    public static final String CTX_BUBBLE_DATA_ID = "BUBBLE_DATA_ID";
    public static final String CTX_BUBBLE_HOME = "BUBBLE_HOME";
    public static final String CTX_SITE = "SITE";

    @Autowired protected BubbleConfiguration configuration;
    @Autowired protected AppDataDAO appDataDAO;
    @Autowired protected AppSiteDAO appSiteDAO;
    @Autowired protected RedisService redis;
    @Autowired protected BubbleNetworkDAO networkDAO;
    @Autowired protected DeviceDAO deviceDAO;

    @Getter @Setter private AppRuleDriver next;

    protected JsonNode config;
    protected JsonNode userConfig;
    protected AppMatcher matcher;
    protected AppRule rule;
    protected Account account;
    protected Device device;

    public Handlebars getHandlebars () { return configuration.getHandlebars(); }

    protected String getDataId(String requestId) { return getDataId(requestId, matcher); }
    public static String getDataId(String requestId, AppMatcher matcher) { return requestId+"/"+matcher.getUuid(); }

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
