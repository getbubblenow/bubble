/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.backup;

import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.BubbleBackup;
import bubble.model.cloud.CloudService;
import bubble.model.cloud.NetworkKeys;
import bubble.server.BubbleConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static bubble.model.cloud.NetworkKeys.PARAM_STORAGE;
import static bubble.model.cloud.NetworkKeys.PARAM_STORAGE_CREDENTIALS;
import static java.io.File.createTempFile;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.cobbzilla.util.daemon.ZillaRuntime.background;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.createTempDir;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.system.CommandShell.execScript;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

@Service @Slf4j
public class NetworkKeysService {

    public static final long KEY_EXPIRATION = MINUTES.toSeconds(15);

    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleConfiguration configuration;

    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService networkPasswordTokens = redis.prefixNamespace(getClass().getSimpleName());

    // keysCode -> status object
    private static final Map<String, BackupPackagingStatus> activeBackupDownloads = new ConcurrentHashMap<>();

    public void registerView(String uuid) {
        final NetworkKeys keys = new NetworkKeys();
        final CloudService storage = cloudDAO.findByUuid(configuration.getThisNetwork().getStorage());
        if (storage == null) {
            log.warn("network storage was null!");
        } else {
            keys.addKey(PARAM_STORAGE, json(storage));
            keys.addKey(PARAM_STORAGE_CREDENTIALS, json(storage.getCredentials()));
        }
        getNetworkPasswordTokens().set(uuid, json(keys), EX, KEY_EXPIRATION);
    }

    @NonNull public NetworkKeys retrieveKeys(@NonNull final String uuid) {
        final String json = getNetworkPasswordTokens().get(uuid);
        if (empty(json)) throw invalidEx("err.retrieveNetworkKeys.notFound");
        getNetworkPasswordTokens().del(uuid);
        return json(json, NetworkKeys.class);
    }

    public void startBackupDownload(@NonNull final String nodeUuid, @NonNull final BubbleBackup backup,
                                    @NonNull final String keysCode, @NonNull final String passphrase) {
        final var storageServiceUuid = configuration.getThisNetwork().getStorage();
        final var storageService = cloudDAO.findByUuid(storageServiceUuid);
        if (storageService == null) throw notFoundEx(storageServiceUuid);
        final var storageDriver = storageService.getStorageDriver(configuration);

        final var status = new BackupPackagingStatus(backup.getUuid());
        if (activeBackupDownloads.putIfAbsent(keysCode, status) != null) {
            throw invalidEx("err.download.error", "Already started");
        }

        background(() -> {
            File backupDir = null;
            try {
                backupDir = createTempDir("backup-");
                final var backupDirAbs = abs(backupDir);
                storageDriver.fetchFiles(nodeUuid, backup.getPath(), backupDirAbs);
                final var backupPackageAbs = abs(createTempFile("backup-", ".tgz.enc"));
                execScript("cd " + backupDirAbs
                           + " && tar -cz *"
                           + " | " + configuration.opensslCmd(true, passphrase) + " > " + backupPackageAbs);
                status.ok(backupPackageAbs);
            } catch (Exception e) {
                status.fail(e.getMessage());
            } finally {
                try {
                    if (backupDir != null) deleteDirectory(backupDir);
                } catch (IOException e) {
                    log.error("Cannot delete tmp backup folder " + backupDir, e);
                }
            }
        }, "NetworkKeysService.startBackupDownload");
    }

    @NonNull public BackupPackagingStatus backupDownloadStatus(@NonNull final String keysCode) {
        final var status = activeBackupDownloads.get(keysCode);
        if (status == null) throw notFoundEx(keysCode);
        if (status.hasError()) throw invalidEx("err.download.error", status.getError());
        return status;
    }

    public void clearBackupDownloadKey(@NonNull final String keysCode) {
        activeBackupDownloads.remove(keysCode);
    }

    public static class BackupPackagingStatus {
        @Getter private boolean done;
        @Getter private final String backupUuid;
        @JsonIgnore @Getter private String packagePath;
        @Getter private String error;
        public boolean hasError() { return !empty(error); }

        private BackupPackagingStatus(@NonNull final String backupUuid) {
            this.done = false;
            this.backupUuid = backupUuid;
            this.packagePath = null;
            this.error = null;
        }

        private BackupPackagingStatus ok(@NonNull final String packagePath) {
            this.done = true;
            this.packagePath = packagePath;
            this.error = null;
            return this;
        }

        private BackupPackagingStatus fail(@NonNull final String error) {
            this.done = true;
            this.packagePath = null;
            this.error = error;
            return this;
        }
    }

}
