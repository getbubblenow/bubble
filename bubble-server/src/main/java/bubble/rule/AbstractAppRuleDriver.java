/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule;

import bubble.dao.app.AppDataDAO;
import bubble.dao.app.AppSiteDAO;
import bubble.dao.cloud.BubbleNetworkDAO;
import bubble.dao.device.DeviceDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.model.device.Device;
import bubble.resources.stream.FilterHttpRequest;
import bubble.resources.stream.FilterMatchersRequest;
import bubble.server.BubbleConfiguration;
import bubble.service.device.StandardDeviceService;
import bubble.service.stream.AppPrimerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.input.ReaderInputStream;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.regex.RegexFilterReader;
import org.cobbzilla.util.io.regex.RegexReplacementFilter;
import org.cobbzilla.util.string.LocaleUtil;
import org.cobbzilla.util.system.Bytes;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.rule.RequestModifierRule.ICON_JS_TEMPLATE;
import static bubble.rule.RequestModifierRule.ICON_JS_TEMPLATE_NAME;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.basename;
import static org.cobbzilla.util.io.regex.RegexReplacementFilter.DEFAULT_PREFIX_REPLACEMENT_WITH_MATCH;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

public abstract class AbstractAppRuleDriver implements AppRuleDriver {

    public static final int RESPONSE_BUFSIZ = (int) (64 * Bytes.KB);

    @Autowired protected BubbleConfiguration configuration;
    @Autowired protected AppDataDAO appDataDAO;
    @Autowired protected AppSiteDAO appSiteDAO;
    @Autowired protected RedisService redis;
    @Autowired protected BubbleNetworkDAO networkDAO;
    @Autowired protected DeviceDAO deviceDAO;
    @Autowired protected AppPrimerService appPrimerService;
    @Autowired protected StandardDeviceService deviceService;

    @Getter @Setter private AppRuleDriver next;

    protected JsonNode config;
    protected JsonNode userConfig;
    protected BubbleApp app;
    protected AppMatcher matcher;
    protected AppRule rule;
    protected Account account;
    protected Device device;

    public <C> Class<C> getConfigClass () { return null; }
    protected Object ruleConfig;
    public <C> C getRuleConfig () { return (C) ruleConfig; }

    public Handlebars getHandlebars () { return configuration.getHandlebars(); }

    protected String getDataId(String requestId) { return getDataId(requestId, matcher); }
    public static String getDataId(String requestId, AppMatcher matcher) { return requestId+"/"+matcher.getUuid(); }

    @Override public void init(JsonNode config,
                               JsonNode userConfig,
                               BubbleApp app,
                               AppRule rule,
                               AppMatcher matcher,
                               Account account,
                               Device device) {
        this.config = config;
        this.userConfig = userConfig;
        this.app = app;
        this.matcher = matcher;
        this.rule = rule;
        this.account = account;
        this.device = device;
        if (getConfigClass() != null) {
            this.ruleConfig = json(json(config), getConfigClass());
        }
    }

    public static final String DEFAULT_INSERTION_REGEX = "<\\s*head[^>]*>";
    public static final String CHARSET_VAR = "{{charset}}";
    public static final String DEFAULT_SCRIPT_OPEN = "<meta charset=\""+CHARSET_VAR+"\"><script>";
    public static final String NONCE_VAR = "{{nonce}}";
    public static final String DEFAULT_SCRIPT_NONCE_OPEN = "<meta charset=\""+CHARSET_VAR+"\"><script nonce=\""+NONCE_VAR+"\">";
    public static final String DEFAULT_SCRIPT_CLOSE = "</script>";

    protected static String insertionRegex (String customRegex) {
        return empty(customRegex) ? DEFAULT_INSERTION_REGEX : customRegex;
    }

    protected static String scriptOpen (FilterHttpRequest filterRequest, String charset, String customNonceOpen, String customNoNonceOpen) {
        return (filterRequest.hasScriptNonce()
                ? (empty(customNonceOpen) ? DEFAULT_SCRIPT_NONCE_OPEN : customNonceOpen).replace(NONCE_VAR, filterRequest.getScriptNonce())
                : (empty(customNoNonceOpen) ? DEFAULT_SCRIPT_OPEN : customNoNonceOpen)
        ).replace(CHARSET_VAR, charset);
    }

    protected static String scriptClose (String customClose) {
        return empty(customClose) ? DEFAULT_SCRIPT_CLOSE : customClose;
    }

    protected String getSiteJsTemplate (String defaultSiteTemplate) {
        return loadTemplate(defaultSiteTemplate, requestModConfig().getSiteJsTemplate());
    }

    @Getter(lazy=true) private final long jarTime = configuration.getBubbleJar().lastModified();

    protected String loadTemplate(String defaultTemplate, String templatePath) {
        if (configuration.getEnvironment().containsKey("DEBUG_RULE_TEMPLATES")) {
            final File templateFile = new File(HOME_DIR + "/debugTemplates/" + basename(templatePath));
            if (templateFile.exists() && templateFile.lastModified() > getJarTime()) {
                if (log.isDebugEnabled()) log.debug("loadTemplate: debug file found and newer than bubble jar, using it: "+abs(templateFile));
                return FileUtil.toStringOrDie(templateFile);
            } else {
                if (log.isDebugEnabled()) log.debug("loadTemplate: debug file not found or older than bubble jar, using default: "+abs(templateFile));
            }
        }
        return defaultTemplate;
    }

    private RequestModifierConfig requestModConfig() {
        if (this instanceof RequestModifierRule) return ((RequestModifierRule) this).getRequestModifierConfig();
        return die("requestModConfig: rule "+getClass().getName()+" does not implement RequestModifierRule");
    }

    @Getter(lazy=true) private final String insertionRegex = insertionRegex(requestModConfig().getInsertionRegex());

    @Getter(lazy=true) private final String scriptClose = scriptClose(requestModConfig().getScriptClose());

    protected InputStream filterInsertJs(InputStream in,
                                         Charset charset,
                                         FilterHttpRequest filterRequest,
                                         Map<String, Object> filterCtx,
                                         String bubbleJsTemplate,
                                         String defaultSiteTemplate,
                                         String siteJsInsertionVar,
                                         boolean showIcon) {
        final RequestModifierConfig modConfig = requestModConfig();
        final String replacement = DEFAULT_PREFIX_REPLACEMENT_WITH_MATCH
                + scriptOpen(filterRequest, charset.name(), modConfig.getScriptOpenNonce(), modConfig.getScriptOpenNoNonce())
                + getBubbleJs(filterRequest, filterCtx, bubbleJsTemplate, defaultSiteTemplate, siteJsInsertionVar, showIcon)
                + getScriptClose();

        final String prefix = getClass().getSimpleName()+".filterInsertJs("+filterRequest.getUrl()+"): ";

        // Do any alternates apply?
        List<BubbleAlternateRegexReplacement> alternates = null;
        if (modConfig.hasAlternateRegexReplacements()) {
            final FilterMatchersRequest request = filterRequest.getMatchersResponse().getRequest();
            for (BubbleAlternateRegexReplacement alt : modConfig.getAlternateRegexReplacements()) {
                if (alt.matches(request.getFqdn())) {
                    if (log.isDebugEnabled()) log.debug(prefix + "including alternate filter: " +alt);
                    if (alternates == null) alternates = new ArrayList<>();
                    alternates.add(alt);
                } else {
                    if (log.isDebugEnabled()) log.debug(prefix + "NOT including alternate filter: " +alt);
                }
            }
        }

        final String filterNamePrefix = getClass().getSimpleName()+".filterInsertJs(" + filterRequest.getUrl() + "): ";
        RegexFilterReader reader;
        if (alternates != null) {
            final BubbleAlternateRegexReplacement firstAlt = alternates.get(0);
            if (log.isInfoEnabled()) log.info(prefix + "using alternate filter (0): " +firstAlt);
            reader = new RegexFilterReader(new InputStreamReader(in, charset), firstAlt.regexFilter(filterRequest, replacement))
                    .setName(filterNamePrefix + "(alt0: "+firstAlt.getFqdnMatch()+") " + firstAlt.getInsertionRegex())
                    .setMaxMatches(1);
            for (int i=1; i<alternates.size(); i++) {
                final BubbleAlternateRegexReplacement alt = alternates.get(i);
                if (log.isInfoEnabled()) log.info(prefix + "using alternate filter (" +i+"): "+alt);
                reader = new RegexFilterReader(reader, alt.regexFilter(filterRequest, replacement))
                        .setName(filterNamePrefix + "(alt"+i+": "+alt.getFqdnMatch()+") " + alt.getInsertionRegex())
                        .setMaxMatches(1);
            }

        } else {
            if (log.isInfoEnabled()) log.info(prefix + "using default filter: " +getInsertionRegex());
            reader = new RegexFilterReader(new InputStreamReader(in, charset), new RegexReplacementFilter(getInsertionRegex(), replacement))
                    .setName(filterNamePrefix + getInsertionRegex())
                    .setMaxMatches(1);
        }

        if (modConfig.hasAdditionalRegexReplacements()) {
            for (BubbleRegexReplacement re : modConfig.getAdditionalRegexReplacements()) {
                if (log.isInfoEnabled()) log.info(prefix + "using additional filter: " +re.getInsertionRegex());
                reader = new RegexFilterReader(reader, re.regexFilter(filterRequest, replacement))
                        .setName(filterNamePrefix+" (additional) "+re.getInsertionRegex());
            }
        }

        return new ReaderInputStream(reader, charset);
    }

    protected String getBubbleJs(FilterHttpRequest filterRequest,
                                 Map<String, Object> filterCtx,
                                 String bubbleJsTemplate,
                                 String defaultSiteTemplate,
                                 String siteJsInsertionVar,
                                 boolean showIcon) {
        final Map<String, Object> ctx = getBubbleJsContext(filterRequest, filterCtx);

        if (!empty(siteJsInsertionVar) && !empty(defaultSiteTemplate)) {
            final String siteJs = HandlebarsUtil.apply(getHandlebars(), getSiteJsTemplate(defaultSiteTemplate), ctx);
            ctx.put(siteJsInsertionVar, siteJs);
        }
        if (showIcon) {
            final String iconJs = loadTemplate(ICON_JS_TEMPLATE, ICON_JS_TEMPLATE_NAME);
            ctx.put(CTX_ICON_JS, HandlebarsUtil.apply(getHandlebars(), iconJs, ctx));
        }
        return HandlebarsUtil.apply(getHandlebars(), bubbleJsTemplate, ctx);
    }

    public static final String CTX_JS_PREFIX = "JS_PREFIX";
    public static final String CTX_PAGE_PREFIX = "PAGE_PREFIX";
    public static final String CTX_PAGE_ONREADY_INTERVAL = "PAGE_ONREADY_INTERVAL";
    public static final String CTX_BUBBLE_REQUEST_ID = "BUBBLE_REQUEST_ID";
    public static final String CTX_BUBBLE_DATA_ID = "BUBBLE_DATA_ID";
    public static final String CTX_BUBBLE_HOME = "BUBBLE_HOME";
    public static final String CTX_BUBBLE_SITE_NAME = "BUBBLE_SITE_NAME";
    public static final String CTX_BUBBLE_APP_NAME = "BUBBLE_APP_NAME";
    public static final String CTX_ICON_JS = "ICON_JS";
    public static final String CTX_APP_CONTROLS_Z_INDEX = "APP_CONTROLS_Z_INDEX";
    public static final String CTX_ACCOUNT_LOCALE = "ACCOUNT_LOCALE";
    public static final String CTX_ACCOUNT_LOCALE_HYPHEN = "ACCOUNT_LOCALE_HYPHEN";
    public static final String CTX_ACCOUNT_LANG = "ACCOUNT_LANG";

    public static final int PAGE_ONREADY_INTERVAL = 50;
    public static final int APP_CONTROLS_Z_INDEX = 2147483640;

    private String getPagePrefix(String requestId) { return "__bubble_page_"+sha256_hex(requestId); }
    private String getJsPrefix(String requestId) { return "__bubble_js_"+sha256_hex(requestId+"_"+getClass().getName()); }

    protected Map<String, Object> getBubbleJsContext(FilterHttpRequest filterRequest, Map<String, Object> filterCtx) {
        final String requestId = filterRequest.getId();
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(CTX_PAGE_PREFIX, getPagePrefix(requestId));
        ctx.put(CTX_JS_PREFIX, getJsPrefix(requestId));
        ctx.put(CTX_PAGE_ONREADY_INTERVAL, PAGE_ONREADY_INTERVAL);
        ctx.put(CTX_APP_CONTROLS_Z_INDEX, APP_CONTROLS_Z_INDEX);
        ctx.put(CTX_BUBBLE_REQUEST_ID, requestId);
        ctx.put(CTX_BUBBLE_HOME, configuration.getPublicUriBase());
        ctx.put(CTX_BUBBLE_SITE_NAME, getSiteName(matcher));
        ctx.put(CTX_BUBBLE_APP_NAME, app.getName());
        ctx.put(CTX_BUBBLE_DATA_ID, getDataId(requestId));

        String locale = filterRequest.getAccount().getLocale();
        if (empty(locale)) locale = configuration.getDefaultLocale();
        ctx.put(CTX_ACCOUNT_LOCALE, locale);
        ctx.put(CTX_ACCOUNT_LOCALE_HYPHEN, locale.replace('_', '-'));
        ctx.put(CTX_ACCOUNT_LANG, LocaleUtil.getLang(locale));
        return ctx;
    }

    private static final ExpirationMap<String, String> siteNameCache = new ExpirationMap<>();
    protected String getSiteName(AppMatcher matcher) {
        return siteNameCache.computeIfAbsent(matcher.getSite(), k -> appSiteDAO.findByAccountAndId(matcher.getAccount(), matcher.getSite()).getName());
    }

}
