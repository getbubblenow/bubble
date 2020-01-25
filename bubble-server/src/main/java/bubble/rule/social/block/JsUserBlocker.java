package bubble.rule.social.block;

import bubble.model.app.AppMatcher;
import bubble.rule.AbstractAppRuleDriver;
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

import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

public class JsUserBlocker extends AbstractAppRuleDriver {

    public static final Class<JsUserBlocker> JSB = JsUserBlocker.class;
    public static final String BUBBLE_JS_TEMPLATE = stream2string(getPackagePath(JSB)+"/"+ JSB.getSimpleName()+".js.hbs");

    public static final String CTX_JS_PREFIX = "JS_PREFIX";
    public static final String CTX_BUBBLE_REQUEST_ID = "BUBBLE_REQUEST_ID";
    public static final String CTX_BUBBLE_DATA_ID = "BUBBLE_DATA_ID";
    public static final String CTX_SITE = "SITE";
    public static final String CTX_APPLY_BLOCKS_JS = "APPLY_BLOCKS_JS";

    @Override public InputStream doFilterResponse(String requestId, InputStream in) {
        final String replacement = "<head><script>" + getBubbleJs(requestId) + "</script>";
        final RegexReplacementFilter filter = new RegexReplacementFilter("<head>", 0, replacement);
        final RegexFilterReader reader = new RegexFilterReader(new InputStreamReader(in), filter).setMaxMatches(1);
        return new ReaderInputStream(reader, UTF8cs);
    }

    @Getter(lazy=true) private final String siteJsTemplate = stream2string(json(config, JsUserBlockerConfig.class).getSiteJsTemplate());

    private String getBubbleJs(String requestId) {
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(CTX_JS_PREFIX, "__bubble_"+sha256_hex(requestId)+"_");
        ctx.put(CTX_BUBBLE_REQUEST_ID, requestId);
        ctx.put(CTX_SITE, getSiteName(matcher));
        ctx.put(CTX_BUBBLE_DATA_ID, requestId+"/"+matcher.getUuid());

        final String siteJs = HandlebarsUtil.apply(getHandlebars(), getSiteJsTemplate(), ctx);
        ctx.put(CTX_APPLY_BLOCKS_JS, siteJs);

        return HandlebarsUtil.apply(getHandlebars(), BUBBLE_JS_TEMPLATE, ctx);
    }

    private ExpirationMap<String, String> siteNameCache = new ExpirationMap<>();
    private String getSiteName(AppMatcher matcher) {
        return siteNameCache.computeIfAbsent(matcher.getSite(), k -> appSiteDAO.findByAccountAndId(matcher.getAccount(), matcher.getSite()).getName());
    }

}
