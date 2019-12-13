package bubble.resources.driver;

import bubble.model.account.Account;
import bubble.model.app.RuleDriver;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.stereotype.Service;

import javax.ws.rs.Path;
import java.util.List;

import static bubble.ApiConstants.DRIVERS_ENDPOINT;
import static org.cobbzilla.wizard.resources.ResourceUtil.userPrincipal;


@Path(DRIVERS_ENDPOINT)
@Service @Slf4j
public class PublicDriversResource extends DriversResourceBase {

    public PublicDriversResource () { super(null); }

    @Override protected List<RuleDriver> list(ContainerRequest ctx) {
        return driverDAO.findPublicTemplates(getAccountUuid(ctx));
    }

    @Override protected RuleDriver find(ContainerRequest ctx, String id) {
        final RuleDriver driver = super.find(ctx, id);
        if (driver == null) return null;
        if (driver.template() && driver.enabled()) return driver;
        final Account caller = userPrincipal(ctx);
        if (caller.admin()) return driver;
        return null;
    }

}
