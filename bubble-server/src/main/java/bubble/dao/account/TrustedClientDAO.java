/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.account;

import bubble.model.account.TrustedClient;
import org.springframework.stereotype.Repository;

import static java.util.UUID.randomUUID;

@Repository
public class TrustedClientDAO extends AccountOwnedEntityDAO<TrustedClient> {

    @Override public Object preCreate(TrustedClient trusted) {
        return super.preCreate(trusted.setTrustId(randomUUID().toString()));
    }

    public TrustedClient findByAccountAndDevice(String accountUuid, String deviceUuid) {
        return findByUniqueFields("account", accountUuid, "device", deviceUuid);
    }

}
