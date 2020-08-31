/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.backup;

import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleBackup;
import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.NetworkKeys;
import bubble.server.BubbleConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.system.Bytes;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.model.cloud.NetworkKeys.PARAM_STORAGE;
import static bubble.model.cloud.NetworkKeys.PARAM_STORAGE_CREDENTIALS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.touch;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

@Service @Slf4j
public class RestoreService {

    public static final File RESTORE_DIR = new File(HOME_DIR, "restore");

    // this is how long the RestoreService will allow a restore, after the API starts
    public static final long RESTORE_WINDOW = HOURS.toMillis(24);
    public static final long RESTORE_WINDOW_SECONDS = RESTORE_WINDOW/1000;

    // this is how long bubble_restore_monitor.sh will allow a restore after it starts
    // we add some time because, in the ansible setup, the script starts (in role bubble) before the
    // API is started (in role finalizer)
    public static final long RESTORE_MONITOR_SCRIPT_TIMEOUT_SECONDS = RESTORE_WINDOW_SECONDS + MINUTES.toSeconds(5);

    private static final long RESTORE_LOCK_TIMEOUT = MINUTES.toMillis(31);
    private static final long RESTORE_DEADLOCK_TIMEOUT = MINUTES.toMillis(30);
    private static final File RESTORE_MARKER_FILE = new File(HOME_DIR, ".restore");

    private static final int BACKUP_ARCHIVE_MANAGEMENT_BUFFER_SIZE = (int) (8 * Bytes.KB);

    @Autowired private CloudServiceDAO cloudDAO;

    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService restoreKeys = redis.prefixNamespace(getClass().getSimpleName());

    @Autowired private BubbleConfiguration configuration;

    public void registerRestore(String restoreKey, NetworkKeys keys) {
        getRestoreKeys().set(restoreKey, json(keys), EX, RESTORE_WINDOW_SECONDS);
    }

    public boolean isValidRestoreKey(String restoreKey) { return getRestoreKeys().exists(restoreKey); }

    public boolean isSelfRestoreStarted() {
        // only one restore may be started, hence
        return getRestoreKeys().keys("*").size() > 0 || RESTORE_MARKER_FILE.exists();
    }

    public boolean restore(String restoreKey, BubbleBackup backup) {
        final String thisNodeUuid = configuration.getThisNode().getUuid();
        final String thisNetworkUuid = configuration.getThisNode().getNetwork();
        String lock = null;
        try {
            lock = getRestoreKeys().lock(thisNetworkUuid, RESTORE_LOCK_TIMEOUT, RESTORE_DEADLOCK_TIMEOUT);

            final String keyJson = getRestoreKeys().get(restoreKey);
            if (keyJson == null) {
                log.error("restore: restoreKey not found: " + restoreKey);
                return false;
            }
            final NetworkKeys networkKeys = json(keyJson, NetworkKeys.class);
            final String storageJson = NameAndValue.find(networkKeys.getKeys(), PARAM_STORAGE);
            final String credentialsJson = NameAndValue.find(networkKeys.getKeys(), PARAM_STORAGE_CREDENTIALS);
            if (storageJson == null || credentialsJson == null) {
                log.error("restore: storage/credentials not found in NetworkKeys");
                return false;
            }

            final String[] existingFiles = RESTORE_DIR.list();
            final var restoreDirAbs = abs(RESTORE_DIR);
            if (existingFiles != null && existingFiles.length > 0) {
                log.error("restore: files already exist in " + restoreDirAbs + ", cannot restore");
                return false;
            }

            final var storageDriver = json(storageJson, CloudService.class)
                    .setCredentials(json(credentialsJson, CloudCredentials.class))
                    .getStorageDriver(configuration);
            try {
                storageDriver.fetchFiles(thisNodeUuid, backup.getPath(), restoreDirAbs);
                log.info("restore: notifying system to restore from backup at: " + restoreDirAbs);
                touch(RESTORE_MARKER_FILE);
                return true;
            } catch (IOException e) {
                log.error("restore: error downloading backup ", e);
                return false;
            }
        } finally {
            if (lock != null) {
                getRestoreKeys().unlock(thisNetworkUuid, lock);
            }
        }
    }
}
