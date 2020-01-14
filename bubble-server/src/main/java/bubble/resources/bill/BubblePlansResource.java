package bubble.resources.bill;

import bubble.dao.bill.BubblePlanDAO;
import bubble.model.account.Account;
import bubble.model.bill.BubblePlan;
import bubble.resources.account.AccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.stereotype.Service;

import javax.ws.rs.Path;

import static bubble.ApiConstants.PLANS_ENDPOINT;
import static bubble.model.bill.BubblePlan.MAX_CHARGENAME_LEN;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Path(PLANS_ENDPOINT)
@Service @Slf4j
public class BubblePlansResource extends AccountOwnedResource<BubblePlan, BubblePlanDAO> {

    public BubblePlansResource() { super(null); }

    @Override protected BubblePlan setReferences(ContainerRequest ctx, Account caller, BubblePlan bubblePlan) {
        if (empty(bubblePlan.getChargeName())) throw invalidEx("err.chargeName.required");
        if (bubblePlan.getChargeName().length() > MAX_CHARGENAME_LEN) throw invalidEx("err.chargeName.length");
        return super.setReferences(ctx, caller, bubblePlan);
    }

}
