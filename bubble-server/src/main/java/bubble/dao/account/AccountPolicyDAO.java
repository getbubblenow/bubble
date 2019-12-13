package bubble.dao.account;

import bubble.model.account.AccountPolicy;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Repository
public class AccountPolicyDAO extends AccountOwnedEntityDAO<AccountPolicy> {

    @Override public Object preCreate(AccountPolicy entity) {
        entity.setUuid(entity.getAccount());
        return super.preCreate(entity);
    }

    public AccountPolicy findSingleByAccount(String accountUuid) {
        final List<AccountPolicy> found = findByAccount(accountUuid);
        return found.isEmpty() ? create(new AccountPolicy().setAccount(accountUuid)) : found.size() > 1 ? die("findSingleByAccount: "+found.size()+" found!") : found.get(0);
    }

}
