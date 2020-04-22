/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.resources.bill;

import bubble.dao.app.BubbleAppDAO;
import bubble.dao.bill.BubblePlanAppDAO;
import bubble.dao.bill.BubblePlanDAO;
import bubble.model.account.Account;
import bubble.model.app.BubbleApp;
import bubble.model.bill.BubblePlan;
import bubble.model.bill.BubblePlanApp;
import bubble.resources.account.AccountOwnedResource;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.ExpirationMap;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static bubble.ApiConstants.EP_APPS;
import static bubble.ApiConstants.PLANS_ENDPOINT;
import static bubble.model.bill.BubblePlan.MAX_CHARGENAME_LEN;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;

@Path(PLANS_ENDPOINT)
@Service @Slf4j
public class BubblePlansResource extends AccountOwnedResource<BubblePlan, BubblePlanDAO> {

    public BubblePlansResource() { super(null); }

    @Autowired private BubblePlanAppDAO planAppDAO;
    @Autowired private BubbleAppDAO appDAO;

    // allow unauthenticated users to read plans
    @Override public Account getAccountForViewById(ContainerRequest ctx) {
        return optionalUserPrincipal(ctx);
    }

    @Override protected BubblePlan setReferences(ContainerRequest ctx, Account caller, BubblePlan bubblePlan) {
        if (empty(bubblePlan.getChargeName())) throw invalidEx("err.chargeName.required");
        if (bubblePlan.getChargeName().length() > MAX_CHARGENAME_LEN) throw invalidEx("err.chargeName.length");
        return super.setReferences(ctx, caller, bubblePlan);
    }

    // BubblePlan objects are global, no need to qualify by account
    @Override protected BubblePlan find(ContainerRequest ctx, String id) { return getDao().findById(id); }
    @Override protected List<BubblePlan> list(ContainerRequest ctx) { return getDao().findAll(); }

    // only admins can create
    @Override protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, BubblePlan request) {
        return caller.admin();
    }

    // only owner can edit
    @Override protected boolean canUpdate(ContainerRequest ctx, Account caller, BubblePlan found, BubblePlan request) {
        return caller.admin() && caller.getUuid().equals(found.getAccount());
    }

    // only owner can delete
    @Override protected boolean canDelete(ContainerRequest ctx, Account caller, BubblePlan found) {
        return caller.admin() && caller.getUuid().equals(found.getAccount());
    }

    @Override protected BubblePlan populate(ContainerRequest ctx, BubblePlan plan) {
        final Account account = optionalUserPrincipal(ctx);
        final List<BubbleApp> apps = getAppsForPlan(plan);
        plan.setApps(apps);
        if (account == null) {
            plan.getApps().forEach(app -> app.setDataConfig(null));
        }
        return super.populate(ctx, plan);
    }

    @Path("/{id}"+EP_APPS)
    public BubblePlanAppsResource getApps(@Context ContainerRequest ctx,
                                          @PathParam("id") String id) {
        final BubblePlan plan = find(ctx, id);
        if (plan == null) throw notFoundEx(id);
        final Account caller = userPrincipal(ctx);
        return configuration.subResource(BubblePlanAppsResource.class, caller, plan);
    }

    private Map<String, List<BubbleApp>> appCache = new ExpirationMap<>();

    private List<BubbleApp> getAppsForPlan(BubblePlan plan) {
        return appCache.computeIfAbsent(plan.getUuid(), planUuid -> appDAO.findByUuids(planAppDAO.findByPlan(planUuid).stream()
                .map(BubblePlanApp::getApp)
                .collect(Collectors.toSet())));
    }
}
