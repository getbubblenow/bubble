package bubble.app.bblock;

import bubble.abp.BlockDecision;
import bubble.abp.BlockListSource;
import bubble.abp.BlockSpec;
import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.RuleDriverDAO;
import bubble.model.account.Account;
import bubble.model.app.AppMatcher;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.model.app.RuleDriver;
import bubble.model.app.config.AppConfigDriver;
import bubble.model.device.Device;
import bubble.rule.bblock.BubbleBlockConfig;
import bubble.rule.bblock.BubbleBlockList;
import bubble.rule.bblock.BubbleBlockRuleDriver;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.string.ValidationRegexes;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
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
public class BubbleBlockAppConfigDriver implements AppConfigDriver {

    public static final String VIEW_manageLists = "manageLists";
    public static final String VIEW_manageList = "manageList";
    public static final String VIEW_manageRules = "manageRules";
    public static final AppMatcher TEST_MATCHER = new AppMatcher();
    public static final Device TEST_DEVICE = new Device();

    @Autowired private RuleDriverDAO driverDAO;
    @Autowired private AppRuleDAO ruleDAO;
    @Autowired private BubbleConfiguration configuration;

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
        }
        throw notFoundEx(view);
    }

    private List<BlockListEntry> loadListEntries(Account account, BubbleApp app, String id) {
        final BubbleBlockList list = loadList(account, app, id);
        if (list == null) throw notFoundEx(id);
        if (list.hasAdditionalEntries()) {
            return Arrays.stream(list.getAdditionalEntries())
                    .map(BlockListEntry::additionalRule)
                    .collect(Collectors.toList());
        }
        return emptyList();
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
        loadDriver(account, rule); // validate proper driver

        final List<BubbleBlockList> blockLists = new ArrayList<>();
        final BubbleBlockConfig blockConfig = json(rule.getConfigJson(), BubbleBlockConfig.class);
        blockLists.addAll( Arrays.stream(blockConfig.getBlockLists())
                .map(list -> list.setRule(rule))
                .collect(Collectors.toList()) );
        return blockLists;
    }

    private RuleDriver loadDriver(Account account, AppRule rule) {
        final RuleDriver driver = driverDAO.findByAccountAndId(account.getUuid(), rule.getDriver());
        if (driver == null || !driver.getDriverClass().equals(BubbleBlockRuleDriver.class.getName())) {
            return die("expected BubbleBlockRuleDriver");
        }
        return driver;
    }

    private AppRule loadRule(Account account, BubbleApp app) {
        final List<AppRule> rules = ruleDAO.findByAccountAndAppAndEnabled(account.getUuid(), app.getUuid());
        if (rules.isEmpty()) return die("loadAllLists: no rule found");
        if (rules.size() > 1) return die("loadAllLists: expected only one enabled rule");
        return rules.get(0);
    }

    public static final String ACTION_enableList = "enableList";
    public static final String ACTION_disableList = "disableList";
    public static final String ACTION_createList = "createList";
    public static final String ACTION_removeList = "removeList";
    public static final String ACTION_updateList = "updateList";
    public static final String ACTION_createRule = "createRule";
    public static final String ACTION_removeRule = "removeRule";
    public static final String ACTION_testUrl = "testUrl";

    public static final String PARAM_URL = "url";
    public static final String PARAM_RULE = "rule";
    public static final String PARAM_TEST_URL = "testUrl";
    public static final String PARAM_TEST_URL_PRIMARY = "testUrlPrimary";

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
            final RuleDriver ruleDriver = loadDriver(account, rule);
            final BubbleBlockRuleDriver unwiredDriver = (BubbleBlockRuleDriver) rule.initDriver(ruleDriver, TEST_MATCHER, account, TEST_DEVICE);
            final BubbleBlockRuleDriver driver = configuration.autowire(unwiredDriver);
            final BlockDecision decision = driver.getDecision(host, path, primary);
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
