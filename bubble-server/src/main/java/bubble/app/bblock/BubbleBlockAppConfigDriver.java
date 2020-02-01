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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

public class BubbleBlockAppConfigDriver implements AppConfigDriver {

    public static final String VIEW_manage_lists = "manage_lists";
    public static final String VIEW_manage_list = "manage_list";
    public static final String VIEW_manage_entries = "manage_entries";

    @Autowired private RuleDriverDAO driverDAO;
    @Autowired private AppRuleDAO ruleDAO;

    @Override public Object getView(Account account, BubbleApp app, String view, Map<String, String> params) {
        final String id = params.get(PARAM_ID);
        switch (view) {
            case VIEW_manage_lists:
                return loadAllLists(account, app);

            case VIEW_manage_list:
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
        return loadAllLists(account, app).stream().filter(list -> list.hasId(id)).findFirst().orElse(null);
    }

    private List<BubbleBlockList> loadAllLists(Account account, BubbleApp app) {
        final List<BubbleBlockList> blockLists = new ArrayList<>();
        final List<AppRule> rules = ruleDAO.findByAccountAndApp(account.getUuid(), app.getUuid());
        for (AppRule rule : rules) {
            final RuleDriver driver = driverDAO.findByAccountAndId(account.getUuid(), rule.getDriver());
            if (driver != null && driver.getDriverClass().equals(BubbleBlockRuleDriver.class.getName())) {
                final BubbleBlockConfig blockConfig = json(rule.getConfigJson(), BubbleBlockConfig.class);
                blockLists.addAll( Arrays.stream(blockConfig.getBlockLists())
                        .map(list -> list.setRule(rule))
                        .collect(Collectors.toList()) );
            }
        }
        return blockLists;
    }

    public static final String ACTION_enable_list = "enable_list";
    public static final String ACTION_disable_list = "disable_list";
    public static final String ACTION_manage_list = "manage_list";
    public static final String ACTION_remove_list = "remove_list";
    public static final String ACTION_add_list = "add_list";
    public static final String ACTION_manage_list_entries = "manage_list_entries";
    public static final String ACTION_remove_rule = "remove_rule";
    public static final String ACTION_create_rule = "create_rule";
    public static final String ACTION_test_url = "test_url";

    @Override public Object takeItemAction(Account account,
                                           BubbleApp app,
                                           String view,
                                           String action,
                                           String id,
                                           Map<String, String> queryParams,
                                           JsonNode data) {
        BubbleBlockList list;

        switch (action) {
            case ACTION_enable_list:
                list = loadList(account, app, id);
                if (list == null) throw notFoundEx(id);
                return updateList(list.setEnabled(true));

            case ACTION_disable_list:
                list = loadList(account, app, id);
                if (list == null) throw notFoundEx(id);
                return updateList(list.setEnabled(false));
        }

        throw notFoundEx(action);
    }

    private BubbleBlockList updateList(BubbleBlockList list) {
        final AppRule rule = list.getRule();
        final BubbleBlockConfig blockConfig = json(rule.getConfigJson(), BubbleBlockConfig.class);
        ruleDAO.update(rule.setConfigJson(json(blockConfig.updateList(list))));
        return list;
    }
}
