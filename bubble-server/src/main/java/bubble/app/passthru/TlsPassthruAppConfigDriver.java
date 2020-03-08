/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.app.passthru;

import bubble.dao.app.AppRuleDAO;
import bubble.model.account.Account;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.model.app.config.AppConfigDriverBase;
import bubble.rule.passthru.TlsPassthruConfig;
import bubble.rule.passthru.TlsPassthruFeed;
import bubble.rule.passthru.TlsPassthruFqdn;
import bubble.rule.passthru.TlsPassthruRuleDriver;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Slf4j
public class TlsPassthruAppConfigDriver extends AppConfigDriverBase {

    public static final String VIEW_manageDomains = "manageDomains";
    public static final String VIEW_manageFeeds = "manageFeeds";

    @Autowired @Getter private AppRuleDAO ruleDAO;

    @Override public Object getView(Account account, BubbleApp app, String view, Map<String, String> params) {
        switch (view) {
            case VIEW_manageDomains:
                return loadManageDomains(account, app);
            case VIEW_manageFeeds:
                return loadManageFeeds(account, app);
        }
        throw notFoundEx(view);
    }

    private Set<TlsPassthruFeed> loadManageFeeds(Account account, BubbleApp app) {
        final TlsPassthruConfig config = getConfig(account, app);
        config.getPassthruSet(); // ensure names are initialized
        return config.getFeedSet();
    }

    private Set<TlsPassthruFqdn> loadManageDomains(Account account, BubbleApp app) {
        final TlsPassthruConfig config = getConfig(account, app);
        return !config.hasFqdnList() ? Collections.emptySet() :
                Arrays.stream(config.getFqdnList())
                        .map(TlsPassthruFqdn::new)
                        .collect(Collectors.toCollection(TreeSet::new));
    }

    private TlsPassthruConfig getConfig(Account account, BubbleApp app) {
        return getConfig(account, app, TlsPassthruRuleDriver.class, TlsPassthruConfig.class);
    }

    public static final String ACTION_addFqdn = "addFqdn";
    public static final String ACTION_removeFqdn = "removeFqdn";
    public static final String ACTION_addFeed = "addFeed";
    public static final String ACTION_removeFeed = "removeFeed";

    public static final String PARAM_FQDN = "passthruFqdn";
    public static final String PARAM_FEED_URL = "feedUrl";

    @Override public Object takeAppAction(Account account, BubbleApp app, String view, String action, Map<String, String> params, JsonNode data) {
        switch (action) {
            case ACTION_addFqdn:
                return addFqdn(account, app, data);
            case ACTION_addFeed:
                return addFeed(account, app, params, data);
        }
        log.debug("takeAppAction: action not found: "+action);
        throw notFoundEx(action);
    }

    private List<TlsPassthruFqdn> addFqdn(Account account, BubbleApp app, JsonNode data) {
        final JsonNode fqdnNode = data.get(PARAM_FQDN);
        if (fqdnNode == null || fqdnNode.textValue() == null || empty(fqdnNode.textValue().trim())) {
            throw invalidEx("err.addFqdn.passthruFqdnRequired");
        }

        final String fqdn = fqdnNode.textValue().trim().toLowerCase();

        final TlsPassthruConfig config = getConfig(account, app)
                .addFqdn(fqdn);

        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        ruleDAO.update(rule.setConfigJson(json(config)));

        return getFqdnList(config);
    }

    private List<TlsPassthruFqdn> getFqdnList(TlsPassthruConfig config) {
        return Arrays.stream(config.getFqdnList())
                .map(TlsPassthruFqdn::new)
                .collect(Collectors.toList());
    }

    private Set<TlsPassthruFeed> addFeed(Account account, BubbleApp app, Map<String, String> params, JsonNode data) {
        final JsonNode urlNode = data.get(PARAM_FEED_URL);
        if (urlNode == null || urlNode.textValue() == null || empty(urlNode.textValue().trim())) {
            throw invalidEx("err.addFeed.feedUrlRequired");
        }

        final String url = urlNode.textValue().trim().toLowerCase();

        final TlsPassthruConfig config = getConfig(account, app);

        final TlsPassthruFeed feed = config.loadFeed(url);
        if (!feed.hasFqdnList()) throw invalidEx("err.addFeed.emptyFqdnList");
        config.addFeed(feed);

        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        ruleDAO.update(rule.setConfigJson(json(config)));

        return config.getFeedSet();
    }

    @Override public Object takeItemAction(Account account, BubbleApp app, String view, String action, String id, Map<String, String> params, JsonNode data) {
        switch (action) {
            case ACTION_removeFqdn:
                return removeFqdn(account, app, id);
            case ACTION_removeFeed:
                return removeFeed(account, app, id);
        }
        log.debug("takeItemAction: action not found: "+action);
        throw notFoundEx(action);
    }

    private List<TlsPassthruFqdn> removeFqdn(Account account, BubbleApp app, String id) {
        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        final TlsPassthruConfig config = getConfig(account, app);
        log.debug("removeFqdn: removing id: "+id+" from config.fqdnList: "+ ArrayUtil.arrayToString(config.getFqdnList()));

        final TlsPassthruConfig updated = config.removeFqdn(id);
        log.debug("removeFqdn: updated.fqdnList: "+ ArrayUtil.arrayToString(updated.getFqdnList()));
        ruleDAO.update(rule.setConfigJson(json(updated)));
        return getFqdnList(updated);
    }

    public Set<TlsPassthruFeed> removeFeed(Account account, BubbleApp app, String id) {
        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        final TlsPassthruConfig config = getConfig(account, app).removeFeed(id);
        ruleDAO.update(rule.setConfigJson(json(config)));
        return config.getFeedSet();
    }

}
