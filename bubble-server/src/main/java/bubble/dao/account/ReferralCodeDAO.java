/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.dao.account;

import bubble.model.account.ReferralCode;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.hibernate.criterion.Restrictions.*;

@Repository
public class ReferralCodeDAO extends AccountOwnedEntityDAO<ReferralCode> {

    public ReferralCode findCodeUsedBy(String accountUuid) { return findByUniqueField("claimedByUuid", accountUuid); }

    public ReferralCode findByName(String code) { return findByUniqueField("name", code); }

    public List<ReferralCode> findByAccountAndClaimed(String accountUuid) {
        return list(criteria().add(and(
                eq("account", accountUuid),
                isNotNull("claimedByUuid"))));
    }

    public List<ReferralCode> findByAccountAndNotClaimed(String accountUuid) {
        return list(criteria().add(and(
                eq("account", accountUuid),
                isNull("claimedByUuid"))));
    }

}
