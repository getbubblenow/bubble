/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.app.request;

import bubble.model.account.Account;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.model.app.config.AppConfigDriverBase;
import bubble.rule.request.HeaderReplacement;
import bubble.rule.request.RequestProtectorConfig;
import bubble.rule.request.RequestProtectorRuleDriver;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Slf4j
public class RequestProtectorAppConfigDriver extends AppConfigDriverBase {

    public static final String VIEW_manageHeaderReplacements = "manageHeaderReplacements";

    @Override public Object getView(Account account, BubbleApp app, String view, Map<String, String> params) {
        switch (view) {
            case VIEW_manageHeaderReplacements:
                return loadManageCookiesReplacements(account, app);
        }
        throw notFoundEx(view);
    }

    private Set<HeaderReplacement> loadManageCookiesReplacements(Account account, BubbleApp app) {
        final RequestProtectorConfig config = getConfig(account, app);
        return config.getHeaderReplacements();
    }

    private RequestProtectorConfig getConfig(Account account, BubbleApp app) {
        return getConfig(account, app, RequestProtectorRuleDriver.class, RequestProtectorConfig.class);
    }

    public static final String ACTION_addHeaderReplacement = "addHeaderReplacement";
    public static final String ACTION_removeHeaderReplacement = "removeHeaderReplacement";

    public static final String PARAM_REGEX = "regex";
    public static final String PARAM_REPLACEMENT = "replacement";

    @Override public Object takeAppAction(Account account, BubbleApp app, String view, String action,
                                          Map<String, String> params, JsonNode data) {
        switch (action) {
            case ACTION_addHeaderReplacement:
                return addHeaderReplacement(account, app, data);
        }
        if (log.isWarnEnabled()) log.warn("takeAppAction: action not found: "+action);
        throw notFoundEx(action);
    }

    private Set<HeaderReplacement> addHeaderReplacement(Account account, BubbleApp app, JsonNode data) {
        final JsonNode regexNode = data.get(PARAM_REGEX);
        if (regexNode == null || regexNode.textValue() == null) {
            throw invalidEx("err.requestProtector.headerRegexRequired");
        }
        final String regex = regexNode.textValue().trim();
        if (empty(regex)) throw invalidEx("err.requestProtector.headerRegexRequired");

        final JsonNode replacementNode = data.get(PARAM_REPLACEMENT);
        final String replacement = (replacementNode == null || replacementNode.textValue() == null)
                                   ? "" : replacementNode.textValue().trim();

        final RequestProtectorConfig config = getConfig(account, app).addHeaderReplacement(regex, replacement);

        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, RequestProtectorRuleDriver.class); // validate proper driver
        if (log.isDebugEnabled()) {
            log.debug("addHeaderReplacement: updating rule: " + rule.getName() + ", adding regex: " + regex);
        }
        ruleDAO.update(rule.setConfigJson(json(config)));

        return config.getHeaderReplacements();
    }

    @Override public Object takeItemAction(Account account, BubbleApp app, String view, String action, String id,
                                           Map<String, String> params, JsonNode data) {
        switch (action) {
            case ACTION_removeHeaderReplacement:
                return removeHeaderReplacement(account, app, id);
        }
        if (log.isWarnEnabled()) log.warn("takeItemAction: action not found: "+action);
        throw notFoundEx(action);
    }

    private Set<HeaderReplacement> removeHeaderReplacement(Account account, BubbleApp app, String regex) {
        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, RequestProtectorRuleDriver.class); // validate proper driver
        final RequestProtectorConfig config = getConfig(account, app);
        if (log.isDebugEnabled()) {
            log.debug("removeHeaderReplacement: removing regex: " + regex + " from config.cookiesReplacements: "
                      + config.getHeaderReplacements().toString());
        }

        final RequestProtectorConfig updated = config.removeHeaderReplacement(regex);
        if (log.isDebugEnabled()) {
            log.debug("removeHeaderReplacement: updated.cookiesReplacements: "
                      + updated.getHeaderReplacements().toString());
        }
        ruleDAO.update(rule.setConfigJson(json(updated)));

        return updated.getHeaderReplacements();
    }
}
