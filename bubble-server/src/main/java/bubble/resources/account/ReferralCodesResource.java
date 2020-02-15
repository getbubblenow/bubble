package bubble.resources.account;

import bubble.dao.account.ReferralCodeDAO;
import bubble.model.account.Account;
import bubble.model.account.ReferralCode;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.URIUtil.queryParams;

@Slf4j
public class ReferralCodesResource extends AccountOwnedResource<ReferralCode, ReferralCodeDAO> {

    private static final String PARAM_REFERRAL_CODE_VIEW = "show";

    public ReferralCodesResource(Account account) { super(account); }

    @Override protected List<ReferralCode> list(Request req, ContainerRequest ctx) {
        final Map<String, String> params = queryParams(ctx.getRequestUri().getQuery());
        if (empty(params) || !params.containsKey(PARAM_REFERRAL_CODE_VIEW)) return super.list(req, ctx);

        switch (params.get(PARAM_REFERRAL_CODE_VIEW)) {
            case "all":   default:          return super.list(req, ctx);
            case "used":  case "claimed":   return getDao().findByAccountAndClaimed(getAccountUuid(ctx));
            case "avail": case "available": return getDao().findByAccountAndNotClaimed(getAccountUuid(ctx));
        }
    }

    @Override protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, ReferralCode request) {
        return caller.admin();
    }

    @Override protected Object daoCreate(ReferralCode toCreate) {
        final List<ReferralCode> createdCodes = new ArrayList<>();
        for (int i=0; i<toCreate.getCount(); i++) {
            createdCodes.add((ReferralCode) super.daoCreate(new ReferralCode()
                    .setAccount(toCreate.getAccount())
                    .setAccountUuid(toCreate.getAccount())
                    .setClaimedBy(null)
                    .setClaimedByUuid(null)
                    .setName()));
        }
        return createdCodes;
    }

    @Override protected boolean canUpdate(ContainerRequest ctx, Account caller, ReferralCode found, ReferralCode request) {
        return caller.admin();
    }

    @Override protected boolean canDelete(ContainerRequest ctx, Account caller, ReferralCode found) {
        return caller.admin();
    }

    @Override protected ReferralCode setReferences(ContainerRequest ctx, Account caller, ReferralCode request) {
        request.setAccountUuid(getAccountUuid(ctx));
        return super.setReferences(ctx, caller, request);
    }

}
