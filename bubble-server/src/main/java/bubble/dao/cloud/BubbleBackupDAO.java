/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.dao.cloud;

import bubble.dao.account.AccountOwnedEntityDAO;
import bubble.model.cloud.BackupStatus;
import bubble.model.cloud.BubbleBackup;
import bubble.service.cloud.StorageService;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.time.TimeUtil.formatDuration;
import static org.cobbzilla.wizard.model.Identifiable.UUID;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository
public class BubbleBackupDAO extends AccountOwnedEntityDAO<BubbleBackup> {

    @Autowired private StorageService storageService;

    @Override public Boolean getHasNameField() { return false; }

    public List<BubbleBackup> findByNetwork(String uuid) { return findByField("network", uuid); }

    public BubbleBackup findNewestSuccessfulByNetwork(String uuid) {
        final List<BubbleBackup> backups = findSuccessfulByNetwork(uuid);
        return backups.isEmpty() ? null : backups.get(0);
    }

    public List<BubbleBackup> findSuccessfulByNetwork(String uuid) {
        return findByFields("network", uuid, "status", BackupStatus.backup_completed);
    }

    public List<BubbleBackup> findStuckByNetwork(String uuid) {
        return findByFieldAndFieldIn("network", uuid, "status", BackupStatus.STUCK);
    }

    @Override public Order getDefaultSortOrder() { return ORDER_CTIME_DESC; }

    public BubbleBackup findByNetworkAndPath(String networkUuid, String path) {
        return findByNetwork(networkUuid).stream()
                .filter(b -> b.getPath().equals(path))
                .findFirst()
                .orElse(null);
    }

    public BubbleBackup findByNetworkAndLabel(String networkUuid, String label) {
        return findByNetwork(networkUuid).stream()
                .filter(b -> b.hasLabel() && b.getLabel().equals(label))
                .findFirst()
                .orElse(null);
    }

    public BubbleBackup findByNetworkAndId(String networkUuid, String id) {
        BubbleBackup found = findByUniqueFields("network", networkUuid, UUID, id);
        if (found != null) return found;
        found = findByNetworkAndPath(networkUuid, id);
        return found != null ? found : findByNetworkAndLabel(networkUuid, id);
    }

    @Override public void delete(String uuid) {
        final BubbleBackup backup = findByUuid(uuid);
        if (!backup.canDelete()) throw invalidEx("err.backup.cannotDelete", "Cannot delete backup with status: "+backup.getStatus()+" and age "+formatDuration(backup.getCtimeAge()), backup.getPath());
        backup.setStatus(BackupStatus.deleting);
        update(backup);
        try {
            storageService.delete(backup.getAccount(), backup.getPath());
            super.delete(uuid);
        } catch (IOException e) {
            die("delete("+uuid+"): "+e);
        }
    }

}
