/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.dao.account;

import bubble.model.account.TrustedClient;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.Identifiable;
import org.springframework.stereotype.Repository;

import static java.util.UUID.randomUUID;

@Repository @Slf4j
public class TrustedClientDAO extends AccountOwnedEntityDAO<TrustedClient> {

    @Override protected String getNameField() { return Identifiable.UUID; }

    @Override public Object preCreate(TrustedClient trusted) {
        return super.preCreate(trusted.setTrustId(randomUUID().toString()));
    }

    public TrustedClient findByAccountAndDevice(String accountUuid, String deviceUuid) {
        return findByUniqueFields("account", accountUuid, "device", deviceUuid);
    }

    public void deleteDevice(String uuid) {
        final int count = bulkDelete("device", uuid);
        if (count <= 1) {
            log.info("deleteDevice: deleted "+count+" TrustedClient records for device "+uuid);
        } else {
            log.warn("deleteDevice: deleted "+count+" TrustedClient records (expected only 1) for device "+uuid);
        }
    }

}
