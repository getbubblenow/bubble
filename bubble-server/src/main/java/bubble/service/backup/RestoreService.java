/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.backup;

import bubble.cloud.storage.StorageServiceDriver;
import bubble.model.cloud.BubbleBackup;
import bubble.model.cloud.CloudCredentials;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.NetworkKeys;
import bubble.notify.storage.StorageListing;
import bubble.server.BubbleConfiguration;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Arrays;

import static bubble.ApiConstants.HOME_DIR;
import static bubble.model.cloud.NetworkKeys.PARAM_STORAGE;
import static bubble.model.cloud.NetworkKeys.PARAM_STORAGE_CREDENTIALS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.security.CryptStream.BUFFER_SIZE;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.cache.redis.RedisService.LOCK_SUFFIX;

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

    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService restoreKeys = redis.prefixNamespace(getClass().getSimpleName());

    @Autowired private BubbleConfiguration configuration;

    public void registerRestore(String restoreKey, NetworkKeys keys) {
        getRestoreKeys().set(restoreKey, json(keys), EX, RESTORE_WINDOW_SECONDS);
    }

    public boolean isValidRestoreKey(String restoreKey) { return getRestoreKeys().exists(restoreKey); }

    public boolean isRestoreStarted(String networkUuid) {
        return getRestoreKeys().exists(networkUuid + LOCK_SUFFIX) || RESTORE_MARKER_FILE.exists();
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
            final CloudService storageService = json(storageJson, CloudService.class)
                    .setCredentials(json(credentialsJson, CloudCredentials.class));

            final StorageServiceDriver storageDriver = storageService.getStorageDriver(configuration);
            final String path = StorageServiceDriver.getPath(backup.getPath());

            final String[] existingFiles = RESTORE_DIR.list();
            if (existingFiles != null && existingFiles.length > 0) {
                log.error("restore: files already exist in " + abs(RESTORE_DIR) + ", cannot restore");
                return false;
            }

            log.info("restore: downloading backup from path=" + path);
            try {
                @Cleanup TempDir temp = new TempDir();
                StorageListing listing = storageDriver.list(thisNodeUuid, path);
                while (true) {
                    Arrays.stream(listing.getKeys()).forEach(k -> {
                        log.info("restore: downloading file: " + k);
                        final File file = new File(abs(temp) + "/" + k);
                        mkdirOrDie(file.getParentFile());
                        try {
                            @Cleanup final InputStream in = storageDriver.read(thisNodeUuid, k);
                            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file), BUFFER_SIZE)) {
                                IOUtils.copyLarge(in, out);
                            }
                            log.info("restore: successfully downloaded file: " + k);
                        } catch (Exception e) {
                            die("restore: error downloading file: " + k + ": " + e);
                        }
                    });
                    if (!listing.isTruncated()) break;
                    listing = storageDriver.listNext(thisNodeUuid, listing.getListingId());
                }

                // all successful, copy directory to a safe place
                copyDirectory(temp, RESTORE_DIR);
                log.info("restore: full download successful, notifying system to restore from backup at: "+abs(RESTORE_DIR));
                touch(RESTORE_MARKER_FILE);
                return true;

            } catch (IOException e) {
                log.error("restore: error downloading backup: " + e);
                return false;
            }
        } finally {
            if (lock != null) {
                getRestoreKeys().unlock(thisNetworkUuid, lock);
            }
        }
    }
}
