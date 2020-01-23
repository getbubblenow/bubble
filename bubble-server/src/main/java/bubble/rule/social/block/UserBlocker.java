package bubble.rule.social.block;

import bubble.dao.app.AppDataDAO;
import bubble.rule.AbstractAppRuleDriver;
import bubble.rule.RequestDecorator;
import bubble.service.cloud.RequestCoordinationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.ReaderInputStream;
import org.cobbzilla.util.io.regex.RegexFilterReader;
import org.cobbzilla.util.io.regex.RegexInsertionFilter;
import org.cobbzilla.util.io.regex.RegexStreamFilter;
import org.cobbzilla.util.system.Bytes;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static bubble.ApiConstants.EP_ASSETS;
import static bubble.ApiConstants.EP_DATA;
import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;

@Slf4j
public class UserBlocker extends AbstractAppRuleDriver {

    private static final int RESPONSE_BUFSIZ = (int) (64 * Bytes.KB);

    @Autowired private RequestCoordinationService requestService;
    @Autowired private AppDataDAO appDataDAO;

    private final String requestId = randomUUID().toString().replace("-", "_");

    // This gets called after autowiring, so `configuration` object will be non-null by now
    @Getter(lazy=true) private final JsonNode fullConfig = initFullConfig();

    private JsonNode initFullConfig() {
        UserBlockerConfig userBlockerConfig;
        try {
            userBlockerConfig = json(json(config), UserBlockerConfig.class);
        } catch (Exception e) {
            return die("initFullConfig: config could not be parsed as a UserBlockerConfig: "+e, e);
        }
        userBlockerConfig.setRule(rule.getUuid());
        userBlockerConfig.setApp(rule.getApp());
        userBlockerConfig.setDriver(rule.getDriver());

        // resolve block control
        final UserBlockerUserConfig userConfigObject = json(json(userConfig), UserBlockerUserConfig.class);
        userBlockerConfig.setUserConfig(userConfigObject);

        final String json = json(userBlockerConfig);
        requestService.set(UserBlocker.class.getName(), requestId, json);

        return json(json, JsonNode.class);
    }

    protected UserBlockerConfig configObject() { return json(getFullConfig(), UserBlockerConfig.class); }

    @Override public InputStream doFilterResponse(InputStream in) {
        final UserBlockerStreamFilter filter = new UserBlockerStreamFilter(matcher, rule);
        filter.configure(getFullConfig());
        filter.setDataDAO(appDataDAO);
        RegexFilterReader reader = new RegexFilterReader(in, RESPONSE_BUFSIZ, filter).setName("mainFilterReader");

        final UserBlockerConfig config = configObject();
        if (config.hasCommentDecorator()) {
            final RequestDecorator commentDecorator = config.getCommentDecorator();

            final Map<String, Object> ctx = new HashMap<>();
            ctx.put("req", requestId);
            ctx.put("uniq", "bubble_"+ requestId);
            ctx.put("commentDecorator", commentDecorator);

            if (commentDecorator.hasBody()) {
                final String body = commentDecorator.hasBody() ? resolveResource(commentDecorator.getBody(), ctx) : "";
                if (body != null && body.trim().length() > 0) {
                    // insert body HTML just after opening <body> tag
                    final RegexStreamFilter bodyFilter = new RegexInsertionFilter()
                            .setAfter(body)
                            .setPattern(startElementRegex("body"));
                    reader = new RegexFilterReader(reader, bodyFilter).setName("bodyReader");
                }
            }
            if (commentDecorator.hasCss()) {
                final String css = commentDecorator.hasCss() ? resolveResource(commentDecorator.getCss(), ctx) : "";
                if (css != null && css.trim().length() > 0) {
                    // insert CSS just after opening <head> tag
                    final RegexStreamFilter bodyFilter = new RegexInsertionFilter()
                            .setAfter("<style>"+css+"</style>")
                            .setPattern(startElementRegex("head"));
                    reader = new RegexFilterReader(reader, bodyFilter).setName("cssReader");
                }
            }
            if (commentDecorator.hasJs()) {
                final String js = resolveResource(commentDecorator.getJs(), ctx);
                if (js != null && js.trim().length() > 0) {
                    // insert CSS just before closing <body> tag
                    final RegexStreamFilter jsFilter = new RegexInsertionFilter()
                            .setBefore("<script>"+js+"</script>")
                            .setPattern(endElementRegex("html"));
                    reader = new RegexFilterReader(reader, jsFilter).setName("jsReader");
                }
            }
        }

        return new ReaderInputStream(reader, UTF8cs);
    }

    public String defaultBlockControlImageUri() {
        // todo: remove /api , just route .bubble to ourselves
        return configuration.getHttp().getBaseUri() + "/.bubble/" + requestId + "/" + getClass().getName() + EP_ASSETS + "/@blockControl.png";
    }

    public String blockActionUri() {
        // todo: remove /api , just route .bubble to ourselves
        return configuration.getHttp().getBaseUri() + "/.bubble/" + requestId + "/" + getClass().getName() + EP_DATA;
    }

    protected String startElementRegex(String el)     { return "(<\\s*"      + el + "[^>]*>)"; }
    protected String endElementRegex(final String el) { return "(<\\s*/\\s*" + el + "\\s*[^>]*>)"; }

    @Override public Handlebars getHandlebars() { return configuration.getHandlebars(); }

}
