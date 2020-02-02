package bubble.app.bblock;

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
import org.cobbzilla.wizard.validation.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpUtil.url2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.string.ValidationRegexes.HTTPS_PATTERN;
import static org.cobbzilla.util.string.ValidationRegexes.HTTP_PATTERN;
import static org.cobbzilla.wizard.model.BasicConstraintConstants.URL_MAXLEN;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

public class BubbleBlockAppConfigDriver implements AppConfigDriver {

    public static final String VIEW_manageLists = "manageLists";
    public static final String VIEW_manageList = "manageList";
    public static final String VIEW_manage_entries = "manage_entries";

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

            case VIEW_manage_entries:
                if (empty(id)) throw notFoundEx(id);
                return loadListEntries(account, app, id);
        }
        throw notFoundEx(view);
    }

    private List<BlockListEntry> loadListEntries(Account account, BubbleApp app, String id) {
        final BubbleBlockList list = loadList(account, app, id);
        return list == null || !list.hasAdditionalEntries() ? emptyList() : Arrays.stream(list.getAdditionalEntries()).map(BlockListEntry::new).collect(Collectors.toList());
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
    public static final String ACTION_manageListRules = "manageListRules";
    public static final String ACTION_createRule = "createRule";
    public static final String ACTION_removeRule = "removeRule";
    public static final String ACTION_test_url = "test_url";

    public static final String PARAM_URL = "url";

    @Override public Object takeAppAction(Account account,
                                          BubbleApp app,
                                          String view,
                                          String action,
                                          Map<String, String> params,
                                          JsonNode data) {
        switch (action) {
            case ACTION_createList:
                return addList(account, app, data);
        }
        throw notFoundEx(action);
    }

    public Object addList(Account account, BubbleApp app, JsonNode data) {

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

        final BubbleBlockList list = new BubbleBlockList(url);
        final String content;
        try {
            content = url2string(url);
            if (empty(content)) throw new IllegalStateException("error fetching url (empty content): "+url);
        } catch (Exception e) {
            throw invalidEx("err.url.invalid");
        }

        // check first few lines for a title and description
        final StringTokenizer st = new StringTokenizer(content, "\n");
        int i=0;
        boolean foundName = false;
        boolean foundDescription = false;
        while (st.hasMoreTokens() && i < 20) {
            i++;
            final String line = st.nextToken().trim();
            if (line.startsWith("!")) {
                if (line.replace(" ", "").startsWith("!Title:")) {
                    int colonPos = line.indexOf(":");
                    list.setName(line.substring(colonPos + 1));
                    foundName = true;
                } else if (line.replace(" ", "").startsWith("!Description:")) {
                    int colonPos = line.indexOf(":");
                    list.setDescription(line.substring(colonPos + 1));
                    foundDescription = true;
                }
                if (foundName && foundDescription) break;
            }
        }

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
                final ValidationResult errors = validate(update, allLists);
                if (errors.isInvalid()) throw invalidEx(errors);
                return updateList(list.update(update));
        }

        throw notFoundEx(action);
    }

    private ValidationResult validate(BubbleBlockList list, List<BubbleBlockList> allLists) {
        final ValidationResult errors = new ValidationResult();
        if (empty(list.getName())) {
            errors.addViolation("err.name.required");
        } else if (list.getName().length() > 300) {
            errors.addViolation("err.name.tooLong");
        } else if (allLists.stream().anyMatch(l -> !l.getId().equals(list.getId()) && l.getName().equals(list.getName()))) {
            errors.addViolation("err.name.alreadyInUse");
        }
        if (!empty(list.getDescription()) && list.getDescription().length() > 4000) {
            errors.addViolation("err.description.length");
        }
        if (empty(list.getUrl())) {
            errors.addViolation("err.url.required");
        } else if (list.getUrl().length() > URL_MAXLEN) {
            errors.addViolation("err.url.length");
        }
        if (list.hasTags() && list.getTagString().length() > 1000) {
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
