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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
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
                blockLists.addAll(asList(blockConfig.getBlockLists()));
            }
        }
        return blockLists;
    }

}
