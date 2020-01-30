package bubble.resources.bill;

import bubble.dao.app.BubbleAppDAO;
import bubble.dao.bill.BubblePlanAppDAO;
import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import bubble.model.bill.BubblePlan;
import bubble.model.bill.BubblePlanApp;
import bubble.resources.account.AccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Slf4j
public class BubblePlanAppsResource extends AccountOwnedResource<BubblePlanApp, BubblePlanAppDAO> {

    private BubblePlan plan;

    public BubblePlanAppsResource(Account account, BubblePlan plan) {
        super(account);
        this.plan = plan;
    }

    @Autowired private BubbleAppDAO appDAO;

    @Override protected BubblePlanApp setReferences(ContainerRequest ctx, Account caller, BubblePlanApp request) {
        final BubbleApp app = appDAO.findByAccountAndId(getAccountUuid(ctx), request.getApp());
        if (app == null) throw notFoundEx(request.getApp());
        request.setApp(app.getUuid());
        request.setPlan(plan.getUuid()); // plan cannot be changed
        return super.setReferences(ctx, caller, request);
    }

}
