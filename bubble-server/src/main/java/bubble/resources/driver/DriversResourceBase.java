package bubble.resources.driver;

import bubble.dao.account.AccountDAO;
import bubble.dao.app.AppRuleDAO;
import bubble.dao.app.RuleDriverDAO;
import bubble.model.account.Account;
import bubble.model.app.RuleDriver;
import bubble.resources.account.AccountOwnedTemplateResource;
import bubble.server.BubbleConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;

import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class DriversResourceBase extends AccountOwnedTemplateResource<RuleDriver, RuleDriverDAO> {

    @Autowired protected BubbleConfiguration configuration;
    @Autowired protected AppRuleDAO ruleDAO;
    @Autowired protected RuleDriverDAO driverDAO;
    @Autowired protected AccountDAO accountDAO;

    public DriversResourceBase(Account account) { super(account); }

}
