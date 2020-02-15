package bubble.resources.account;

import bubble.dao.account.ReferralCodeDAO;
import bubble.model.account.Account;
import bubble.model.account.ReferralCode;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.jersey.server.ContainerRequest;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ReferralCodesResource extends AccountOwnedResource<ReferralCode, ReferralCodeDAO> {

    public ReferralCodesResource(Account account) { super(account); }

    @Override protected boolean canCreate(Request req, ContainerRequest ctx, Account caller, ReferralCode request) {
        return caller.admin();
    }

    @Override protected Object daoCreate(ReferralCode toCreate) {
        final List<ReferralCode> createdCodes = new ArrayList<>();
        for (int i=0; i<toCreate.getCount(); i++) {
            createdCodes.add((ReferralCode) super.daoCreate(new ReferralCode()
                    .setAccount(toCreate.getAccount())
                    .setAccountUuid(toCreate.getAccount())
                    .setUsedBy(null)
                    .setUsedByUuid(null)
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
