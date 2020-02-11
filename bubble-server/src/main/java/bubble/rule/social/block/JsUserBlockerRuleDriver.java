package bubble.rule.social.block;

import bubble.model.app.AppMatcher;
import bubble.resources.stream.FilterHttpRequest;
import bubble.rule.AbstractAppRuleDriver;
import bubble.rule.AppRuleDriver;
import lombok.Getter;
import org.apache.commons.io.input.ReaderInputStream;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.regex.RegexFilterReader;
import org.cobbzilla.util.io.regex.RegexReplacementFilter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static org.cobbzilla.util.http.HttpContentTypes.isHtml;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

public class JsUserBlockerRuleDriver extends AbstractAppRuleDriver {

    public static final Class<JsUserBlockerRuleDriver> JSB = JsUserBlockerRuleDriver.class;
    public static final String BUBBLE_JS_TEMPLATE = stream2string(getPackagePath(JSB)+"/"+ JSB.getSimpleName()+".js.hbs");

    public static final String CTX_APPLY_BLOCKS_JS = "APPLY_BLOCKS_JS";

    @Override public InputStream doFilterResponse(FilterHttpRequest filterRequest, InputStream in) {
        if (!isHtml(filterRequest.getContentType())) return in;

        final String replacement = "<head><meta charset=\"UTF-8\"><script>" + getBubbleJs(filterRequest.getId()) + "</script>";
        final RegexReplacementFilter filter = new RegexReplacementFilter("<head>", replacement);
        final RegexFilterReader reader = new RegexFilterReader(new InputStreamReader(in), filter).setMaxMatches(1);
        return new ReaderInputStream(reader, UTF8cs);
    }

    @Getter(lazy=true) private final String siteJsTemplate = stream2string(json(config, JsUserBlockerConfig.class).getSiteJsTemplate());

    private String getBubbleJs(String requestId) {
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(CTX_JS_PREFIX, AppRuleDriver.getJsPrefix(requestId));
        ctx.put(CTX_BUBBLE_REQUEST_ID, requestId);
        ctx.put(CTX_BUBBLE_HOME, configuration.getPublicUriBase());
        ctx.put(CTX_SITE, getSiteName(matcher));
        ctx.put(CTX_BUBBLE_DATA_ID, getDataId(requestId));

        final String siteJs = HandlebarsUtil.apply(getHandlebars(), getSiteJsTemplate(), ctx);
        ctx.put(CTX_APPLY_BLOCKS_JS, siteJs);

        return HandlebarsUtil.apply(getHandlebars(), BUBBLE_JS_TEMPLATE, ctx);
    }

    private ExpirationMap<String, String> siteNameCache = new ExpirationMap<>();
    private String getSiteName(AppMatcher matcher) {
        return siteNameCache.computeIfAbsent(matcher.getSite(), k -> appSiteDAO.findByAccountAndId(matcher.getAccount(), matcher.getSite()).getName());
    }

}
