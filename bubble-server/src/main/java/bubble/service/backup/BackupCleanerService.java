/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://git.bubblev.org/bubblev/bubble/src/branch/master/LICENSE.md
 */
package bubble.service.backup;

import bubble.dao.cloud.BubbleBackupDAO;
import bubble.model.cloud.BackupStatus;
import bubble.model.cloud.BubbleBackup;
import bubble.server.BubbleConfiguration;
import bubble.service.cloud.StandardStorageService;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Service @Slf4j
public class BackupCleanerService extends SimpleDaemon {

    public static final long STARTUP_DELAY = DAYS.toMillis(3);
    public static final long SLEEP_INTERVAL = DAYS.toMillis(1);

    public static final long MIN_STUCK_AGE = DAYS.toMillis(3);
    public static final long CLEAN_NOW_TIMEOUT = SECONDS.toMillis(25);

    public static final int MAX_BACKUPS = 7;

    @Override protected long getStartupDelay() { return STARTUP_DELAY; }
    @Override protected long getSleepTime() { return SLEEP_INTERVAL; }
    @Override protected boolean canInterruptSleep() { return true; }

    @Override public void processException(Exception e) throws Exception {
        log.error("Error cleaning backups: "+e);
    }

    @Autowired private BubbleConfiguration configuration;
    @Autowired private BubbleBackupDAO backupDAO;
    @Autowired private StandardStorageService storageService;

    private final AtomicReference<List<BubbleBackup>> lastCleaned = new AtomicReference<>();

    public List<BubbleBackup> cleanNow() {
        final List<BubbleBackup> currentCleaned;
        synchronized (lastCleaned) {
            currentCleaned = lastCleaned.get();
        }

        List<BubbleBackup> newlyCleaned = null;
        final long start = now();
        while (now() - start < CLEAN_NOW_TIMEOUT) {
            synchronized (lastCleaned) {
                newlyCleaned = lastCleaned.get();
            }
            if (newlyCleaned != currentCleaned) break;
            sleep(SECONDS.toMillis(1), "cleanNow: waiting for BackupCleanerService to clean backups");
        }

        if (newlyCleaned == currentCleaned) {
            if (newlyCleaned == null) throw invalidEx("err.backupCleaner.neverRun");
            throw invalidEx("err.backupCleaner.didNotRun");
        }
        return newlyCleaned;
    }

    @Override protected void process() {
        final List<BubbleBackup> deleted = new ArrayList<>();
        try {
            final String network = configuration.getThisNode().getNetwork();
            final List<BubbleBackup> successfulBackups = backupDAO.findSuccessfulByNetwork(network);
            if (successfulBackups.size() <= MAX_BACKUPS) {
                log.info("Found " + successfulBackups.size() + " backups <= " + MAX_BACKUPS + " -- not deleting any backups");
                return;
            }
            while (successfulBackups.size() > MAX_BACKUPS) {
                final BubbleBackup backup = successfulBackups.get(successfulBackups.size() - 1);
                deleteBackup(backup, deleted);
                successfulBackups.remove(successfulBackups.size() - 1);
            }

            backupDAO.findStuckByNetwork(network).stream()
                    .filter(b -> b.getCtimeAge() > MIN_STUCK_AGE)
                    .forEach(backup -> deleteBackup(backup, deleted));
        } finally {
            synchronized (lastCleaned) {
                lastCleaned.set(deleted);
            }
        }
    }

    private void deleteBackup(BubbleBackup backup, List<BubbleBackup> deleted) {
        if (backup.getStatus() != BackupStatus.deleting) {
            final String account = configuration.getThisNode().getAccount();
            backup.setStatus(BackupStatus.deleting);
            backupDAO.update(backup);
            final String path = backup.getPath();
            try {
                storageService.delete(account, path);
                backupDAO.delete(backup.getUuid());
                log.info("deleteBackup: successfully deleted backup: "+ path);
                deleted.add(backup);
            } catch (IOException e) {
                log.error("Error deleting backup " + path + " from storage: " + e);
                backup.setStatus(BackupStatus.delete_error);
                backupDAO.update(backup);
            }
        }
    }

}
