/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.app.passthru;

import bubble.dao.app.AppRuleDAO;
import bubble.model.account.Account;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.model.app.config.AppConfigDriverBase;
import bubble.rule.passthru.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ArrayUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Slf4j
public class TlsPassthruAppConfigDriver extends AppConfigDriverBase {

    public static final String VIEW_managePassthruDomains = "managePassthruDomains";
    public static final String VIEW_managePassthruFeeds = "managePassthruFeeds";
    public static final String VIEW_manageFlexDomains = "manageFlexDomains";
    public static final String VIEW_manageFlexFeeds = "manageFlexFeeds";

    @Autowired @Getter private AppRuleDAO ruleDAO;

    @Override public Object getView(Account account, BubbleApp app, String view, Map<String, String> params) {
        switch (view) {
            case VIEW_managePassthruDomains:
                return loadManagePassthruDomains(account, app);
            case VIEW_managePassthruFeeds:
                return loadManagePassthuFeeds(account, app);
            case VIEW_manageFlexDomains:
                return loadManageFlexDomains(account, app);
            case VIEW_manageFlexFeeds:
                return loadManageFlexFeeds(account, app);
        }
        throw notFoundEx(view);
    }

    private Set<TlsPassthruFeed> loadManagePassthuFeeds(Account account, BubbleApp app) {
        final TlsPassthruConfig config = getConfig(account, app);
        config.getPassthruSet(); // ensure names are initialized
        return config.getPassthruFeedSet();
    }

    private Set<TlsPassthruFqdn> loadManagePassthruDomains(Account account, BubbleApp app) {
        final TlsPassthruConfig config = getConfig(account, app);
        return !config.hasPassthruFqdnList() ? Collections.emptySet() :
                Arrays.stream(config.getPassthruFqdnList())
                        .map(TlsPassthruFqdn::new)
                        .collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<FlexFeed> loadManageFlexFeeds(Account account, BubbleApp app) {
        if (!account.admin()) throw forbiddenEx();
        final TlsPassthruConfig config = getConfig(account, app);
        config.getFlexSet(); // ensure names are initialized
        return config.getFlexFeedSet();
    }

    private Set<FlexFqdn> loadManageFlexDomains(Account account, BubbleApp app) {
        if (!account.admin()) throw forbiddenEx();
        final TlsPassthruConfig config = getConfig(account, app);
        return !config.hasFlexFqdnList() ? Collections.emptySet() :
                Arrays.stream(config.getFlexFqdnList())
                        .map(FlexFqdn::new)
                        .collect(Collectors.toCollection(TreeSet::new));
    }

    private TlsPassthruConfig getConfig(Account account, BubbleApp app) {
        return getConfig(account, app, TlsPassthruRuleDriver.class, TlsPassthruConfig.class);
    }

    public static final String ACTION_addPassthruFqdn = "addPassthruFqdn";
    public static final String ACTION_addPassthruFeed = "addPassthruFeed";
    public static final String ACTION_removePassthruFqdn = "removePassthruFqdn";
    public static final String ACTION_removePassthruFeed = "removePassthruFeed";

    public static final String ACTION_addFlexFqdn = "addFlexFqdn";
    public static final String ACTION_addFlexFeed = "addFlexFeed";
    public static final String ACTION_removeFlexFqdn = "removeFlexFqdn";
    public static final String ACTION_removeFlexFeed = "removeFlexFeed";

    public static final String PARAM_PASSTHRU_FQDN = "passthruFqdn";
    public static final String PARAM_PASSTHRU_FEED_URL = "passthruFeedUrl";
    public static final String PARAM_FLEX_FQDN = "flexFqdn";
    public static final String PARAM_FLEX_FEED_URL = "flexFeedUrl";

    @Override public Object takeAppAction(Account account, BubbleApp app, String view, String action, Map<String, String> params, JsonNode data) {
        switch (action) {
            case ACTION_addPassthruFqdn:
                return addPassthruFqdn(account, app, data);
            case ACTION_addPassthruFeed:
                return addPassthruFeed(account, app, params, data);
            case ACTION_addFlexFqdn:
                return addFlexFqdn(account, app, data);
            case ACTION_addFlexFeed:
                return addFlexFeed(account, app, params, data);
        }
        if (log.isWarnEnabled()) log.warn("takeAppAction: action not found: "+action);
        throw notFoundEx(action);
    }

    private List<TlsPassthruFqdn> addPassthruFqdn(Account account, BubbleApp app, JsonNode data) {
        final JsonNode fqdnNode = data.get(PARAM_PASSTHRU_FQDN);
        if (fqdnNode == null || fqdnNode.textValue() == null || empty(fqdnNode.textValue().trim())) {
            throw invalidEx("err.passthruFqdn.passthruFqdnRequired");
        }

        final String fqdn = fqdnNode.textValue().trim().toLowerCase();
        final TlsPassthruConfig config = getConfig(account, app).addPassthruFqdn(fqdn);

        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        if (log.isDebugEnabled()) log.debug("addPassthruFqdn: updating rule: "+rule.getName()+", adding fqdn: "+fqdn);
        ruleDAO.update(rule.setConfigJson(json(config)));

        return getPassthruFqdnList(config);
    }

    private List<TlsPassthruFqdn> getPassthruFqdnList(TlsPassthruConfig config) {
        return Arrays.stream(config.getPassthruFqdnList())
                .map(TlsPassthruFqdn::new)
                .collect(Collectors.toList());
    }

    private Set<TlsPassthruFeed> addPassthruFeed(Account account, BubbleApp app, Map<String, String> params, JsonNode data) {
        final JsonNode urlNode = data.get(PARAM_PASSTHRU_FEED_URL);
        if (urlNode == null || urlNode.textValue() == null || empty(urlNode.textValue().trim())) {
            throw invalidEx("err.passthruFeedUrl.feedUrlRequired");
        }

        final String url = urlNode.textValue().trim().toLowerCase();
        final TlsPassthruConfig config = getConfig(account, app);

        final TlsPassthruFeed feed = config.loadFeed(url);
        if (!feed.hasFqdnList()) throw invalidEx("err.passthruFeedUrl.emptyFqdnList");
        config.addPassthruFeed(feed);

        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        ruleDAO.update(rule.setConfigJson(json(config)));

        return config.getPassthruFeedSet();
    }

    private List<TlsPassthruFqdn> addFlexFqdn(Account account, BubbleApp app, JsonNode data) {
        if (!account.admin()) throw forbiddenEx();
        final JsonNode fqdnNode = data.get(PARAM_FLEX_FQDN);
        if (fqdnNode == null || fqdnNode.textValue() == null || empty(fqdnNode.textValue().trim())) {
            throw invalidEx("err.flexFqdn.flexFqdnRequired");
        }

        final String fqdn = fqdnNode.textValue().trim().toLowerCase();
        final TlsPassthruConfig config = getConfig(account, app).addFlexFqdn(fqdn);

        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        if (log.isDebugEnabled()) log.debug("addFlexFqdn: updating rule: "+rule.getName()+", adding fqdn: "+fqdn);
        ruleDAO.update(rule.setConfigJson(json(config)));

        return getFlexFqdnList(config);
    }

    private List<TlsPassthruFqdn> getFlexFqdnList(TlsPassthruConfig config) {
        return Arrays.stream(config.getFlexFqdnList())
                .map(TlsPassthruFqdn::new)
                .collect(Collectors.toList());
    }

    private Set<TlsPassthruFeed> addFlexFeed(Account account, BubbleApp app, Map<String, String> params, JsonNode data) {
        if (!account.admin()) throw forbiddenEx();
        final JsonNode urlNode = data.get(PARAM_FLEX_FEED_URL);
        if (urlNode == null || urlNode.textValue() == null || empty(urlNode.textValue().trim())) {
            throw invalidEx("err.flexFeedUrl.feedUrlRequired");
        }

        final String url = urlNode.textValue().trim().toLowerCase();

        final TlsPassthruConfig config = getConfig(account, app);

        final TlsPassthruFeed feed = config.loadFeed(url);
        if (!feed.hasFqdnList()) throw invalidEx("err.flexFeedUrl.emptyFqdnList");
        config.addPassthruFeed(feed);

        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        ruleDAO.update(rule.setConfigJson(json(config)));

        return config.getPassthruFeedSet();
    }

    @Override public Object takeItemAction(Account account, BubbleApp app, String view, String action, String id, Map<String, String> params, JsonNode data) {
        switch (action) {
            case ACTION_removePassthruFqdn:
                return removePassthruFqdn(account, app, id);
            case ACTION_removePassthruFeed:
                return removePassthruFeed(account, app, id);
            case ACTION_removeFlexFqdn:
                return removeFlexFqdn(account, app, id);
            case ACTION_removeFlexFeed:
                return removeFlexFeed(account, app, id);
        }
        if (log.isWarnEnabled()) log.warn("takeItemAction: action not found: "+action);
        throw notFoundEx(action);
    }

    private List<TlsPassthruFqdn> removePassthruFqdn(Account account, BubbleApp app, String id) {
        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        final TlsPassthruConfig config = getConfig(account, app);
        if (log.isDebugEnabled()) log.debug("removePassthruFqdn: removing id: "+id+" from config.fqdnList: "+ ArrayUtil.arrayToString(config.getPassthruFqdnList()));

        final TlsPassthruConfig updated = config.removePassthruFqdn(id);
        if (log.isDebugEnabled()) log.debug("removePassthruFqdn: updated.fqdnList: "+ ArrayUtil.arrayToString(updated.getPassthruFqdnList()));
        ruleDAO.update(rule.setConfigJson(json(updated)));
        return getPassthruFqdnList(updated);
    }

    public Set<TlsPassthruFeed> removePassthruFeed(Account account, BubbleApp app, String id) {
        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        final TlsPassthruConfig config = getConfig(account, app).removePassthruFeed(id);
        ruleDAO.update(rule.setConfigJson(json(config)));
        return config.getPassthruFeedSet();
    }

    private List<TlsPassthruFqdn> removeFlexFqdn(Account account, BubbleApp app, String id) {
        if (!account.admin()) throw forbiddenEx();
        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        final TlsPassthruConfig config = getConfig(account, app);
        if (log.isDebugEnabled()) log.debug("removeFlexFqdn: removing id: "+id+" from config.fqdnList: "+ ArrayUtil.arrayToString(config.getPassthruFqdnList()));

        final TlsPassthruConfig updated = config.removeFlexFqdn(id);
        if (log.isDebugEnabled()) log.debug("removeFlexFqdn: updated.fqdnList: "+ ArrayUtil.arrayToString(updated.getPassthruFqdnList()));
        ruleDAO.update(rule.setConfigJson(json(updated)));
        return getFlexFqdnList(updated);
    }

    public Set<TlsPassthruFeed> removeFlexFeed(Account account, BubbleApp app, String id) {
        if (!account.admin()) throw forbiddenEx();
        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, TlsPassthruRuleDriver.class); // validate proper driver
        final TlsPassthruConfig config = getConfig(account, app).removeFlexFeed(id);
        ruleDAO.update(rule.setConfigJson(json(config)));
        return config.getPassthruFeedSet();
    }

}
