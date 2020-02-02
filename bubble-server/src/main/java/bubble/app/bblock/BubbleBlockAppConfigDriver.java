package bubble.app.bblock;

import bubble.abp.BlockListSource;
import bubble.abp.BlockSpec;
import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.RuleDriverDAO;
import bubble.model.account.Account;
import bubble.model.app.AppRule;
import bubble.model.app.BubbleApp;
import bubble.model.app.RuleDriver;
import bubble.model.app.config.AppConfigDriver;
import bubble.rule.bblock.BubbleBlockConfig;
import bubble.rule.bblock.BubbleBlockList;
import bubble.rule.bblock.BubbleBlockRuleDriver;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
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

    @Autowired private RuleDriverDAO driverDAO;
    @Autowired private AppRuleDAO ruleDAO;

    @Override public Object getView(Account account, BubbleApp app, String view, Map<String, String> params) {
        final String id = params.get(PARAM_ID);
        switch (view) {
            case VIEW_manageLists:
                return loadAllLists(account, app);

            case VIEW_manageList:
                if (empty(id)) throw notFoundEx(id);
                return loadList(account, app, id);

            case VIEW_manageRules:
                if (empty(id)) throw notFoundEx(id);
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
    public static final String ACTION_test_url = "test_url";

    public static final String PARAM_URL = "url";
    public static final String PARAM_RULE = "rule";

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
        }
        throw notFoundEx(action);
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
            BlockSpec.parse(line);
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
        final List<BubbleBlockList> customLists = loadAllLists(account, app).stream().filter(list -> !list.hasUrl()).collect(Collectors.toList());
        if (customLists.isEmpty()) throw invalidEx("err.removeRule.noCustomList");
        if (customLists.size() > 1) throw invalidEx("err.removeRule.multipleCustomLists");
        final BubbleBlockList builtin = customLists.get(0);
        return updateList(builtin.removeRule(id));
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
