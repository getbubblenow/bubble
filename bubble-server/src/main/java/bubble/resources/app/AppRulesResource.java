package bubble.resources.app;

import bubble.dao.app.RuleDriverDAO;
import bubble.dao.app.AppRuleDAO;
import bubble.model.account.Account;
import bubble.model.app.RuleDriver;
import bubble.model.app.BubbleApp;
import bubble.model.app.AppRule;
import bubble.resources.account.AccountOwnedTemplateResource;
import lombok.Getter;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import java.util.List;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class AppRulesResource extends AccountOwnedTemplateResource<AppRule, AppRuleDAO> {

    @Autowired private RuleDriverDAO driverDAO;

    @Getter private BubbleApp app;

    public AppRulesResource(Account account, BubbleApp app) {
        super(account);
        this.app = app;
    }

    @Override protected List<AppRule> list(ContainerRequest ctx) {
        return getDao().findByAccountAndApp(getAccountUuid(ctx), app.getUuid());
    }

    @Override protected AppRule find(ContainerRequest ctx, String id) {
        return getDao().findByAccountAndAppAndId(getAccountUuid(ctx), app.getUuid(), id);
    }

    @Override protected AppRule setReferences(ContainerRequest ctx, Account caller, AppRule appRule) {

        final RuleDriver driver = driverDAO.findByAccountAndId(app.getAccount(), appRule.getDriver());
        if (driver == null) throw notFoundEx(appRule.getDriver());

        appRule.setDriver(driver.getUuid());
        appRule.setApp(app.getUuid());

        return super.setReferences(ctx, caller, appRule);
    }

}
