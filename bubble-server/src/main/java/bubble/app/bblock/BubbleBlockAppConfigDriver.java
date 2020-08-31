/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.app.bblock;

import bubble.abp.BlockDecision;
import bubble.abp.BlockListSource;
import bubble.abp.BlockSpec;
import bubble.dao.app.AppDataDAO;
import bubble.dao.app.AppMatcherDAO;
import bubble.dao.app.AppSiteDAO;
import bubble.model.account.Account;
import bubble.model.app.*;
import bubble.model.app.config.AppConfigDriverBase;
import bubble.model.device.Device;
import bubble.rule.bblock.*;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.string.ValidationRegexes;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

import static bubble.rule.bblock.BubbleBlockRuleDriver.fqdnFromKey;
import static java.util.Collections.emptySet;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpSchemes.SCHEME_HTTPS;
import static org.cobbzilla.util.http.HttpSchemes.isHttpOrHttps;
import static org.cobbzilla.util.http.URIUtil.getHost;
import static org.cobbzilla.util.http.URIUtil.getPath;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.ValidationRegexes.HTTPS_PATTERN;
import static org.cobbzilla.util.string.ValidationRegexes.HTTP_PATTERN;
import static org.cobbzilla.wizard.model.BasicConstraintConstants.URL_MAXLEN;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Slf4j
public class BubbleBlockAppConfigDriver extends AppConfigDriverBase {

    public static final String VIEW_manageLists = "manageLists";
    public static final String VIEW_manageList = "manageList";
    public static final String VIEW_manageRules = "manageRules";
    public static final String VIEW_manageUserAgents = "manageUserAgents";
    public static final String VIEW_manageHideStats = "manageHideStats";
    public static final AppMatcher TEST_MATCHER = new AppMatcher();
    public static final Device TEST_DEVICE = new Device();
    public static final String PREFIX_APPDATA_HIDE_STATS = "hideStats_";
    public static final String DEFAULT_MATCHER_NAME = "BubbleBlockMatcher";
    public static final String DEFAULT_SITE_NAME = "All_Sites";

    @Autowired private BubbleConfiguration configuration;
    @Autowired private AppDataDAO dataDAO;
    @Autowired private AppMatcherDAO matcherDAO;
    @Autowired private AppSiteDAO siteDAO;

    private final Map<Account, AppMatcher> defaultMatchers = new ExpirationMap<>(4, HOURS.toMillis(1));
    private AppMatcher getDefaultMatcher (Account account, BubbleApp app) {
        return defaultMatchers.computeIfAbsent(account, a -> matcherDAO.findByAccountAndAppAndId(account.getUuid(), app.getUuid(), DEFAULT_MATCHER_NAME));
    }

    private final Map<Account, AppSite> defaultSites = new ExpirationMap<>(4, HOURS.toMillis(1));
    private AppSite getDefaultSite (Account account, BubbleApp app) {
        return defaultSites.computeIfAbsent(account, a -> siteDAO.findByAccountAndAppAndId(account.getUuid(), app.getUuid(), DEFAULT_SITE_NAME));
    }

    @Override public Object getView(Account account, BubbleApp app, String view, Map<String, String> params) {
        String id = params.get(PARAM_ID);
        switch (view) {
            case VIEW_manageLists:
                return loadAllLists(account, app);

            case VIEW_manageList:
                if (empty(id)) throw notFoundEx(id);
                return loadList(account, app, id);

            case VIEW_manageRules:
                if (empty(id)) {
                    final BubbleBlockList builtinList = getBuiltinList(account, app);
                    if (builtinList == null) throw notFoundEx(id);
                    id = builtinList.getId();
                }
                return loadListEntries(account, app, id);

            case VIEW_manageUserAgents:
                return loadUserAgentBlocks(account, app);

            case VIEW_manageHideStats:
                return loadHideStats(account, app);
        }
        throw notFoundEx(view);
    }

    private Set<BlockListEntry> loadListEntries(Account account, BubbleApp app, String id) {
        final BubbleBlockList list = loadList(account, app, id);
        if (list == null) throw notFoundEx(id);
        if (list.hasAdditionalEntries()) {
            return Arrays.stream(list.getAdditionalEntries())
                    .map(BlockListEntry::additionalRule)
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        return emptySet();
    }

    private BubbleBlockList loadList(Account account, BubbleApp app, String id) {
        final List<BubbleBlockList> allLists = loadAllLists(account, app);
        return findList(allLists, id);
    }

    private BubbleBlockList findList(List<BubbleBlockList> allLists, String id) {
        return allLists.stream().filter(list -> list.hasId(id)).findFirst().orElse(null);
    }

    private List<BubbleBlockList> loadAllLists(Account account, BubbleApp app) {

        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, BubbleBlockRuleDriver.class); // validate proper driver

        final BubbleBlockConfig blockConfig = json(rule.getConfigJson(), BubbleBlockConfig.class);
        return Arrays.stream(blockConfig.getBlockLists())
                .map(list -> list.setRule(rule))
                .collect(Collectors.toList());
    }

    private BubbleUserAgentBlock[] loadUserAgentBlocks(Account account, BubbleApp app) {

        final AppRule rule = loadRule(account, app);
        loadDriver(account, rule, BubbleBlockRuleDriver.class); // validate proper driver

        final BubbleBlockConfig blockConfig = json(rule.getConfigJson(), BubbleBlockConfig.class);
        final BubbleUserAgentBlock[] blocks = blockConfig.getUserAgentBlocks();
        return empty(blocks) ? BubbleUserAgentBlock.NO_BLOCKS : blocks;
    }

    private List<BubbleHideStats> loadHideStats(Account account, BubbleApp app) {
        return dataDAO.findByAccountAndAppAndAndKeyPrefix(account.getUuid(), app.getUuid(), PREFIX_APPDATA_HIDE_STATS)
                .stream()
                .filter(d -> Boolean.parseBoolean(d.getData()))
                .map(BubbleHideStats::new)
                .collect(Collectors.toList());
    }

    public static final String ACTION_enableList = "enableList";
    public static final String ACTION_disableList = "disableList";
    public static final String ACTION_createList = "createList";
    public static final String ACTION_removeList = "removeList";
    public static final String ACTION_updateList = "updateList";
    public static final String ACTION_createRule = "createRule";
    public static final String ACTION_removeRule = "removeRule";
    public static final String ACTION_createUserAgentBlock = "createUserAgentBlock";
    public static final String ACTION_removeUserAgentBlock = "removeUserAgentBlock";
    public static final String ACTION_testUrl = "testUrl";
    public static final String ACTION_removeHideStats = "removeHideStats";
    public static final String ACTION_createHideStats = "createHideStats";

    public static final String PARAM_URL = "url";
    public static final String PARAM_RULE = "rule";
    public static final String PARAM_USER_AGENT_REGEX = "userAgentRegex";
    public static final String PARAM_TEST_URL = "testUrl";
    public static final String PARAM_TEST_USER_AGENT = "testUserAgent";
    public static final String PARAM_TEST_URL_PRIMARY = "testUrlPrimary";
    public static final String PARAM_FQDN = "fqdn";

    @Override public Object takeAppAction(Account account,
                                          BubbleApp app,
                                          String view,
                                          String action,
                                          Map<String, String> params,
                                          JsonNode data) {
        switch (action) {
            case ACTION_createList:
                return addList(account, app, data);
            case ACTION_createRule:
                return addRule(account, app, params, data);
            case ACTION_testUrl:
                return testUrl(account, app, data);
            case ACTION_createUserAgentBlock:
                return createUserAgentBlock(account, app, params, data);
            case ACTION_createHideStats:
                return createHideStats(account, app, params, data);
        }
        throw notFoundEx(action);
    }

    private BubbleBlockList testUrl(Account account, BubbleApp app, JsonNode data) {
        final JsonNode testUrlNode = data.get(PARAM_TEST_URL);
        if (testUrlNode == null || empty(testUrlNode.textValue())) throw invalidEx("err.testUrl.required");
        String testUrl = testUrlNode.textValue();

        final JsonNode testUrlPrimaryNode = data.get(PARAM_TEST_URL_PRIMARY);
        final boolean primary = testUrlPrimaryNode == null || testUrlPrimaryNode.booleanValue();

        if (!isHttpOrHttps(testUrl)) testUrl = SCHEME_HTTPS + testUrl;

        final String userAgent;
        final JsonNode userAgentNode = data.get(PARAM_TEST_USER_AGENT);
        if (userAgentNode == null || empty(userAgentNode.textValue())) {
            userAgent = "";
        } else {
            userAgent = userAgentNode.textValue();
        }

        final String host;
        final String path;
        try {
            host = getHost(testUrl);
            path = getPath(testUrl);
        } catch (Exception e) {
            throw invalidEx("err.testUrl.invalid", "Test URL was not valid", shortError(e));
        }
        if (empty(host) || !ValidationRegexes.HOST_PATTERN.matcher(host).matches()) {
            throw invalidEx("err.testUrl.invalidHostname", "Test URL was not valid");
        }

        try {
            final AppRule rule = loadRule(account, app);
            final RuleDriver ruleDriver = loadDriver(account, rule, BubbleBlockRuleDriver.class);
            final BubbleBlockRuleDriver unwiredDriver = (BubbleBlockRuleDriver) rule.initDriver(app, ruleDriver, TEST_MATCHER, account, TEST_DEVICE);
            final BubbleBlockRuleDriver driver = configuration.autowire(unwiredDriver);
            final BlockDecision decision = driver.getDecision(host, path, userAgent, primary);
            return getBuiltinList(account, app).setResponse(decision);

        } catch (Exception e) {
            throw invalidEx("err.testRule.loadingTestDriver", "Error loading test driver", shortError(e));
        }
    }

    private BubbleBlockList addRule(Account account, BubbleApp app, Map<String, String> params, JsonNode data) {

        final String id = params.get(PARAM_ID);
        if (id == null) throw invalidEx("err.id.required");

        final JsonNode rule = data.get(PARAM_RULE);
        if (rule == null || rule.textValue() == null) throw invalidEx("err.rule.required");

        final String line = rule.textValue().trim();
        final BubbleBlockList list = loadList(account, app, id);
        if (list == null) throw notFoundEx(id);
        if (list.hasAdditionalEntries()) {
            if (list.hasEntry(line)) {
                throw invalidEx("err.rule.alreadyExists");
            }
        }
        try {
            final List<BlockSpec> specs = BlockSpec.parse(line);
            if (log.isDebugEnabled()) log.debug("addRule: parsed line ("+line+"): "+json(specs));
        } catch (Exception e) {
            log.warn("addRule: invalid line ("+line+"): "+shortError(e));
            throw invalidEx("err.rule.invalid", "Error parsing rule", e.getMessage());
        }
        return updateList(list.addEntry(line));
    }

    public BubbleBlockList addList(Account account, BubbleApp app, JsonNode data) {

        final JsonNode urlNode = data.get(PARAM_URL);
        if (urlNode == null) throw invalidEx("err.url.required");
        final String url = urlNode.textValue().trim();
        if (empty(url)) throw invalidEx("err.url.required");
        if (!HTTP_PATTERN.matcher(url).matches() && !HTTPS_PATTERN.matcher(url).matches()) {
            throw invalidEx("err.url.invalid");
        }

        final List<BubbleBlockList> allLists = loadAllLists(account, app);
        if (allLists.stream().anyMatch(list -> list.getUrl().equals(url))) {
            throw invalidEx("err.url.alreadyExists");
        }

        final BlockListSource source = new BlockListSource().setUrl(url);
        try {
            source.download();
        } catch (Exception e) {
            throw invalidEx("err.url.invalid");
        }
        final BubbleBlockList list = new BubbleBlockList(source);
        final AppRule rule = loadRule(account, app);
        final BubbleBlockConfig blockConfig = json(rule.getConfigJson(), BubbleBlockConfig.class);
        ruleDAO.update(rule.setConfigJson(json(blockConfig.updateList(list))));
        return list;
    }

    private BubbleUserAgentBlock createUserAgentBlock(Account account, BubbleApp app, Map<String, String> params, JsonNode data) {

        final JsonNode rule = data.get(PARAM_RULE);
        final String urlRegex;
        if (rule == null || rule.textValue() == null) {
            urlRegex = ".*";
        } else {
            urlRegex = rule.textValue().trim();
        }

        final JsonNode userAgentRegexNode = data.get(PARAM_USER_AGENT_REGEX);
        if (userAgentRegexNode == null) {
            throw invalidEx("err.userAgentRegex.required");
        }
        final String userAgentRegex = userAgentRegexNode.textValue().trim();

        final AppRule appRule = loadRule(account, app);
        final BubbleBlockConfig blockConfig = json(appRule.getConfigJson(), BubbleBlockConfig.class);
        final BubbleUserAgentBlock uaBlock = new BubbleUserAgentBlock()
                .setUrlRegex(urlRegex)
                .setUserAgentRegex(userAgentRegex);
        ruleDAO.update(appRule.setConfigJson(json(blockConfig.addUserAgentBlock(uaBlock))));
        return uaBlock;
    }

    private BubbleUserAgentBlock[] removeUserAgentBlock(Account account, BubbleApp app, String id) {
        final AppRule appRule = loadRule(account, app);
        final BubbleBlockConfig blockConfig = json(appRule.getConfigJson(), BubbleBlockConfig.class);
        ruleDAO.update(appRule.setConfigJson(json(blockConfig.removeUserAgentBlock(id))));
        return blockConfig.getUserAgentBlocks();
    }


    private BubbleHideStats createHideStats(Account account, BubbleApp app, Map<String, String> params, JsonNode data) {
        final JsonNode fqdnNode = data.get(PARAM_FQDN);
        if (fqdnNode == null) throw invalidEx("err.fqdn.required");
        final String fqdn = fqdnNode.textValue();
        final BubbleHideStats hideStats = new BubbleHideStats(fqdn);
        final AppData appData = dataDAO.set(hideStats.toAppData(account, app, getDefaultMatcher(account, app), getDefaultSite(account, app)));
        hideStats.setId(appData.getUuid());
        return hideStats;
    }

    private List<BubbleHideStats> removeHideStats(Account account, BubbleApp app, String uuid) {
        final AppData appData = dataDAO.findByAccountAndId(account.getUuid(), uuid);
        if (appData != null) {
            final String fqdn = fqdnFromKey(appData.getKey());  // sanity check that key is in the right format
            dataDAO.set(appData.setData("false"));
        }
        return loadHideStats(account, app);
    }

    @Override public Object takeItemAction(Account account,
                                           BubbleApp app,
                                           String view,
                                           String action,
                                           String id,
                                           Map<String, String> queryParams,
                                           JsonNode data) {
        BubbleBlockList list;

        switch (action) {
            case ACTION_enableList:
                list = loadList(account, app, id);
                if (list == null) throw notFoundEx(id);
                return updateList(list.setEnabled(true));

            case ACTION_disableList:
                list = loadList(account, app, id);
                if (list == null) throw notFoundEx(id);
                return updateList(list.setEnabled(false));

            case ACTION_removeList:
                list = loadList(account, app, id);
                if (list == null) throw notFoundEx(id);
                if (empty(list.getUrl())) {
                    // only one empty-URL list is allowed (the custom bubble list) and it cannot be removed
                    throw invalidEx("err.removeList.cannotRemoveBuiltinList");
                }
                return removeList(list);

            case ACTION_updateList:
                final List<BubbleBlockList> allLists = loadAllLists(account, app);
                list = findList(allLists, id);
                if (list == null) throw notFoundEx(id);
                final BubbleBlockList update = json(data, BubbleBlockList.class);
                final ValidationResult errors = validate(list, update, allLists);
                if (errors.isInvalid()) throw invalidEx(errors);
                return updateList(list.update(update));

            case ACTION_removeRule:
                return removeRule(account, app, id);

            case ACTION_removeUserAgentBlock:
                return removeUserAgentBlock(account, app, id);

            case ACTION_removeHideStats:
                return removeHideStats(account, app, id);
        }

        throw notFoundEx(action);
    }

    private BubbleBlockList removeRule(Account account, BubbleApp app, String id) {
        final BubbleBlockList builtin = getBuiltinList(account, app);
        return updateList(builtin.removeRule(id));
    }

    private BubbleBlockList getBuiltinList(Account account, BubbleApp app) {
        final List<BubbleBlockList> customLists = loadAllLists(account, app).stream().filter(list -> !list.hasUrl()).collect(Collectors.toList());
        if (customLists.isEmpty()) throw invalidEx("err.removeRule.noCustomList");
        if (customLists.size() > 1) throw invalidEx("err.removeRule.multipleCustomLists");
        return customLists.get(0);
    }

    private ValidationResult validate(BubbleBlockList list, BubbleBlockList request, List<BubbleBlockList> allLists) {
        final ValidationResult errors = new ValidationResult();
        if (empty(request.getName())) {
            errors.addViolation("err.name.required");
        } else if (request.getName().length() > 300) {
            errors.addViolation("err.name.tooLong");
        } else if (allLists.stream().anyMatch(l -> !l.getId().equals(request.getId()) && l.getName().equals(request.getName()))) {
            errors.addViolation("err.name.alreadyInUse");
        }
        if (!empty(request.getDescription()) && request.getDescription().length() > 4000) {
            errors.addViolation("err.description.length");
        }
        if (!list.hasUrl()) {
            // this is the built-in list
            if (!empty(request.getUrl())) {
                errors.addViolation("err.url.cannotSetOnBuiltinList");
            }
        } else {
            if (empty(request.getUrl())) {
                errors.addViolation("err.url.required");
            } else if (request.getUrl().length() > URL_MAXLEN) {
                errors.addViolation("err.url.length");
            }
        }
        if (request.hasTags() && request.getTagString().length() > 1000) {
            errors.addViolation("err.tagString.length");
        }
        return errors;
    }

    private BubbleBlockList removeList(BubbleBlockList list) {
        final AppRule rule = list.getRule();
        final BubbleBlockConfig blockConfig = json(rule.getConfigJson(), BubbleBlockConfig.class);
        ruleDAO.update(rule.setConfigJson(json(blockConfig.removeList(list))));
        return list;
    }

    private BubbleBlockList updateList(BubbleBlockList list) {
        final AppRule rule = list.getRule();
        final BubbleBlockConfig blockConfig = json(rule.getConfigJson(), BubbleBlockConfig.class);
        ruleDAO.update(rule.setConfigJson(json(blockConfig.updateList(list))));
        return list;
    }
}
