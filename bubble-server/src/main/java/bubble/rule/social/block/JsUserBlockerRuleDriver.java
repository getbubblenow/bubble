/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.rule.social.block;

import bubble.resources.stream.FilterHttpRequest;
import bubble.rule.AbstractAppRuleDriver;
import bubble.rule.RequestModifierConfig;
import bubble.rule.RequestModifierRule;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

import static org.cobbzilla.util.io.FileUtil.basename;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;

@Slf4j
public class JsUserBlockerRuleDriver extends AbstractAppRuleDriver implements RequestModifierRule {

    public static final Class<JsUserBlockerRuleDriver> JSB = JsUserBlockerRuleDriver.class;
    public static final String BUBBLE_JS_TEMPLATE_NAME = JSB.getSimpleName() + ".js.hbs";
    public static final String BUBBLE_JS_TEMPLATE = stream2string(getPackagePath(JSB) + "/" + BUBBLE_JS_TEMPLATE_NAME);

    public static final String CTX_APPLY_BLOCKS_JS = "APPLY_BLOCKS_JS";

    @Override public boolean couldModify(FilterHttpRequest request) { return true; }

    @Override public <C> Class<C> getConfigClass() { return (Class<C>) JsUserBlockerConfig.class; }

    @Override public RequestModifierConfig getRequestModifierConfig() { return getRuleConfig(); }

    @Getter(lazy=true) private final String defaultSiteJsTemplate = stream2string(getRequestModifierConfig().getSiteJsTemplate());

    protected String getSiteJsTemplate() {
        return loadTemplate(getDefaultSiteJsTemplate(), basename(getRequestModifierConfig().getSiteJsTemplate()));
    }

    @Override public InputStream doFilterResponse(FilterHttpRequest filterRequest, InputStream in) {
        if (!filterRequest.isHtml()) return in;
        final String bubbleJsTemplate = loadTemplate(BUBBLE_JS_TEMPLATE, BUBBLE_JS_TEMPLATE_NAME);
        final String siteJsTemplate = getSiteJsTemplate();
        return filterInsertJs(in, filterRequest, null, bubbleJsTemplate, siteJsTemplate, CTX_APPLY_BLOCKS_JS, true);
    }
}
