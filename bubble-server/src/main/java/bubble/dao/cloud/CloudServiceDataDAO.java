/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.dao.cloud;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.cloud.CloudServiceData;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.cobbzilla.wizard.model.Identifiable.UUID;

@Repository
public class CloudServiceDataDAO extends AccountOwnedEntityDAO<CloudServiceData> {

    @Override protected String getNameField() { return "key"; }

    public List<CloudServiceData> findByAccountAndCloud(String accountUuid, String cloudUuid) {
        return findByFields("account", accountUuid, "cloud", cloudUuid);
    }

    public CloudServiceData findByAccountAndCloudAndId(String accountUuid, String cloudUuid, String id) {
        final CloudServiceData found = findByUniqueFields("account", accountUuid, "cloud", cloudUuid, "key", id);
        return found != null ? found : findByUniqueFields("account", accountUuid, "cloud", cloudUuid, UUID, id);
    }

    public CloudServiceData findByCloudAndKey(String cloudUuid, String key) {
        return findByUniqueFields("cloud", cloudUuid, "key", key);
    }

    public List<CloudServiceData> findByCloud(String cloudUuid) {
        return findByField("cloud", cloudUuid);
    }

    public List<CloudServiceData> findByCloudAndPrefix(String cloudUuid, String prefix) {
        return findByFieldEqualAndFieldLike("cloud", cloudUuid, "key", prefix+"%");
    }

    // never limit results at the DAO level, we need to see all data in the resources
    @Override protected int getFinderMaxResults() { return Integer.MAX_VALUE; }

    public List<CloudServiceData> findByPrefix(String prefix) { return findByPrefixNewerThan(prefix, null); }

    public List<CloudServiceData> findByPrefixNewerThan(String prefix, Long lastMod) {
        if (lastMod == null) return findByFieldLike("key", prefix+"%");
        return findByFieldLikeAndNewerThan("key", prefix+"%", lastMod);
    }

}
