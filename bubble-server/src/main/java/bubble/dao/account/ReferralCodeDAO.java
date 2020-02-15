package bubble.dao.account;

import bubble.model.account.ReferralCode;
import org.springframework.stereotype.Repository;

@Repository
public class ReferralCodeDAO extends AccountOwnedEntityDAO<ReferralCode> {

    public ReferralCode findCodeUsedBy(String accountUuid) { return findByUniqueField("usedBy", accountUuid); }

    public ReferralCode findByName(String code) { return findByUniqueField("name", code); }

}
