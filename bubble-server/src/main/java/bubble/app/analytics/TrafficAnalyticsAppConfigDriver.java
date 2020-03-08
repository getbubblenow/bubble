/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.app.analytics;

import bubble.model.account.Account;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.model.app.config.AppConfigDriverBase;
import bubble.rule.analytics.TrafficAnalyticsConfig;
import bubble.rule.analytics.TrafficAnalyticsRuleDriver;
import bubble.rule.passthru.TlsPassthruRuleDriver;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;

import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Slf4j
public class TrafficAnalyticsAppConfigDriver extends AppConfigDriverBase {

    public static final String VIEW_manageFilters = "manageFilters";

    @Override public Object getView(Account account, BubbleApp app, String view, Map<String, String> params) {
        switch (view) {
            case VIEW_manageFilters:
                return loadManageFilters(account, app);
        }
        log.debug("getView: view not found: "+view);
        throw notFoundEx(view);
    }

    private Object loadManageFilters(Account account, BubbleApp app) {
        final TrafficAnalyticsConfig config = getConfig(account, app);
        return config.getPatterns();
    }

    private TrafficAnalyticsConfig getConfig(Account account, BubbleApp app) {
        return getConfig(account, app, TrafficAnalyticsRuleDriver.class, TrafficAnalyticsConfig.class);
    }

    public static final String ACTION_addFilter = "addFilter";
    public static final String ACTION_removeFilter = "removeFilter";

    public static final String PARAM_FILTER = "analyticsFilter";

    @Override public Object takeAppAction(Account account, BubbleApp app, String view, String action, Map<String, String> params, JsonNode data) {
        switch (action) {
            case ACTION_addFilter:
                return addFilter(account, app, data);
        }
        log.debug("takeAppAction: action not found: "+action);
        throw notFoundEx(action);
    }

    private Object addFilter(Account account, BubbleApp app, JsonNode data) {
        final JsonNode filterNode = data.get(PARAM_FILTER);
        if (filterNode == null || filterNode.textValue() == null || empty(filterNode.textValue().trim())) {
            throw invalidEx("err.addFilter.analyticsFilterRequired");
        }

        final String filter = filterNode.textValue().trim().toLowerCase();

        final TrafficAnalyticsConfig config = getConfig(account, app)
                .addFilter(filter);

        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        ruleDAO.update(rule.setConfigJson(json(config)));

        return config.getPatterns();
    }

    @Override public Object takeItemAction(Account account, BubbleApp app, String view, String action, String id, Map<String, String> params, JsonNode data) {
        switch (action) {
            case ACTION_removeFilter:
                return removeFilter(account, app, id);
        }
        log.debug("takeAppAction: action not found: "+action);
        throw notFoundEx(action);
    }

    private Object removeFilter(Account account, BubbleApp app, String id) {
        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TrafficAnalyticsRuleDriver.class); // validate proper driver
        final TrafficAnalyticsConfig config = getConfig(account, app);

        final TrafficAnalyticsConfig updated = config.removeFilter(id);
        log.debug("removeFilter: updated.filterPatterns: "+ ArrayUtil.arrayToString(updated.getFilterPatterns()));
        ruleDAO.update(rule.setConfigJson(json(updated)));
        return config.getPatterns();
    }
}
