/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.social.block;

import bubble.model.app.AppMatcher;
import bubble.resources.stream.FilterHttpRequest;
import bubble.rule.AbstractAppRuleDriver;
import bubble.rule.AppRuleDriver;
import bubble.rule.BubbleRegexReplacement;
import lombok.Getter;
import org.apache.commons.io.input.ReaderInputStream;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.regex.RegexFilterReader;
import org.cobbzilla.util.io.regex.RegexReplacementFilter;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static bubble.ApiConstants.HOME_DIR;
import static org.cobbzilla.util.http.HttpContentTypes.isHtml;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.io.regex.RegexReplacementFilter.DEFAULT_PREFIX_REPLACEMENT_WITH_MATCH;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

public class JsUserBlockerRuleDriver extends AbstractAppRuleDriver {

    public static final Class<JsUserBlockerRuleDriver> JSB = JsUserBlockerRuleDriver.class;
    public static final String BUBBLE_JS_TEMPLATE = stream2string(getPackagePath(JSB)+"/"+ JSB.getSimpleName()+".js.hbs");

    public static final String CTX_APPLY_BLOCKS_JS = "APPLY_BLOCKS_JS";

    public static final String DEFAULT_INSERTION_REGEX = "<\\s*head[^>]*>";
    public static final String DEFAULT_SCRIPT_OPEN = "<meta charset=\"UTF-8\"><script>";
    public static final String NONCE_VAR = "{{nonce}}";
    public static final String DEFAULT_SCRIPT_NONCE_OPEN = "<meta charset=\"UTF-8\"><script nonce=\""+NONCE_VAR+"\">";
    public static final String DEFAULT_SCRIPT_CLOSE = "</script>";

    @Override public boolean couldModify(FilterHttpRequest request) { return true; }

    @Getter(lazy=true) private final JsUserBlockerConfig userBlockerConfig = json(config, JsUserBlockerConfig.class);

    @Override public InputStream doFilterResponse(FilterHttpRequest filterRequest, InputStream in) {
        if (!isHtml(filterRequest.getContentType())) return in;
        final String replacement = DEFAULT_PREFIX_REPLACEMENT_WITH_MATCH
                + getScriptOpen(filterRequest)
                + getBubbleJs(filterRequest.getId())
                + getScriptClose();

        final RegexReplacementFilter filter = new RegexReplacementFilter(getInsertionRegex(), replacement);
        RegexFilterReader reader = new RegexFilterReader(new InputStreamReader(in), filter).setMaxMatches(1);
        if (getUserBlockerConfig().hasAdditionalRegexReplacements()) {
            for (BubbleRegexReplacement re : getUserBlockerConfig().getAdditionalRegexReplacements()) {
                final RegexReplacementFilter f = new RegexReplacementFilter(re.getInsertionRegex(), re.getReplacement());
                reader = new RegexFilterReader(reader, f);
            }
        }

        return new ReaderInputStream(reader, UTF8cs);
    }

    @Getter(lazy=true) private final String insertionRegex = getUserBlockerConfig().hasInsertionRegex()
            ? getUserBlockerConfig().getInsertionRegex()
            : DEFAULT_INSERTION_REGEX;

    public String getScriptOpen(FilterHttpRequest filterRequest) {
        log.info("getScriptOption: scriptNonce="+filterRequest.getScriptNonce());
        if (filterRequest.hasScriptNonce()) {
            return getUserBlockerConfig().hasScriptOpen()
                    ? getUserBlockerConfig().getScriptOpen().replace(NONCE_VAR, filterRequest.getScriptNonce())
                    : DEFAULT_SCRIPT_NONCE_OPEN.replace(NONCE_VAR, filterRequest.getScriptNonce());
        } else {
            return getUserBlockerConfig().hasScriptOpen()
                    ? getUserBlockerConfig().getScriptOpen()
                    : DEFAULT_SCRIPT_OPEN;
        }
    }

    @Getter(lazy=true) private final String scriptClose = getUserBlockerConfig().hasScriptClose()
            ? getUserBlockerConfig().getScriptClose()
            : DEFAULT_SCRIPT_CLOSE;

    @Getter(lazy=true) private final String _siteJsTemplate = stream2string(getUserBlockerConfig().getSiteJsTemplate());

    public String getSiteJsTemplate () {
        if (configuration.getEnvironment().containsKey("DEBUG_JS_SITE_TEMPLATES")) {
            final File jsTemplateFile = new File(HOME_DIR + "/siteJsTemplates/" + getUserBlockerConfig().getSiteJsTemplate());
            if (jsTemplateFile.exists()) {
                return FileUtil.toStringOrDie(jsTemplateFile);
            }
        }
        return get_siteJsTemplate();
    }

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
