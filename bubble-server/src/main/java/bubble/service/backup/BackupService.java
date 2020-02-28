/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
 */
package bubble.service.backup;

import bubble.cloud.storage.StorageServiceDriver;
import bubble.cloud.storage.local.LocalStorageConfig;
import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.BubbleBackupDAO;
import bubble.dao.cloud.BubbleNodeDAO;
import bubble.dao.cloud.CloudServiceDAO;
import bubble.model.cloud.*;
import bubble.model.cloud.notify.NotificationReceipt;
import bubble.server.BubbleConfiguration;
import bubble.service.boot.StandardSelfNodeService;
import bubble.service.notify.NotificationService;
import bubble.service.cloud.StandardStorageService;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.cobbzilla.util.io.FilesystemWalker;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.system.Bytes;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static bubble.ApiConstants.*;
import static bubble.cloud.storage.local.LocalStorageDriver.LOCAL_STORAGE;
import static bubble.cloud.storage.local.LocalStorageDriver.SUFFIX_META;
import static bubble.model.cloud.notify.NotificationType.register_backup;
import static bubble.server.BubbleServer.isRestoreMode;
import static bubble.service.boot.StandardSelfNodeService.*;
import static java.util.concurrent.TimeUnit.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYYMMDD;

@Service @Slf4j
public class BackupService extends SimpleDaemon {

    public static final String DOTFILES = "dotfiles/";

    @Autowired private BubbleConfiguration configuration;
    @Autowired private AccountDAO accountDAO;
    @Autowired private RedisService redis;
    @Autowired private CloudServiceDAO cloudDAO;
    @Autowired private BubbleNodeDAO nodeDAO;
    @Autowired private BubbleBackupDAO backupDAO;
    @Autowired private StandardStorageService storageService;
    @Autowired private BackupCleanerService backupCleanerService;
    @Autowired private StandardSelfNodeService selfNodeService;
    @Autowired private NotificationService notificationService;

    @Getter(lazy=true) private final RedisService backupMetadata = redis.prefixNamespace(getClass().getSimpleName());
    @Getter(lazy=true) private final String metadataStateKey = "network."+configuration.getThisNetwork().getUuid()+".state";
    @Getter(lazy=true) private final String lastBackupKey = "network."+configuration.getThisNetwork().getUuid()+".lastBackup";

    // todo: make these configurable. maybe tags on the BubbleNetwork?
    public static final long BACKUP_INTERVAL = DAYS.toMillis(1) + MINUTES.toMillis(10);
    public static final long BR_STATE_LOCK_TIMEOUT = MINUTES.toMillis(30);
    public static final long BR_STATE_DEADLOCK_TIMEOUT = MINUTES.toMillis(25);
    public static final long BR_CHECK_INTERVAL = HOURS.toMillis(1);
    public static final long STARTUP_DELAY = MINUTES.toMillis(5);

    public static final String BACKUP_PREFIX = "bubble_backups";

    @Override protected long getStartupDelay() { return STARTUP_DELAY; }
    @Override protected long getSleepTime() { return BR_CHECK_INTERVAL + RandomUtils.nextLong(0, MINUTES.toMillis(15)); }

    public BackupService () { start(); }

    @Override protected void init() throws Exception { backupCleanerService.start(); }

    @Override protected void process() {
        if (!configuration.backupsEnabled()) {
            log.info("backup: backups not enabled, not backing up");
            return;
        }
        if (isRestoreMode()) {
            log.info("backup: restore mode is active, not backing up");
            return;
        }
        try {
            final BackupDecision decision = shouldBackup();
            if (decision.isBackupNow()) {
                log.info("backup: shouldBackup returned true, starting backup");
                final BubbleNetwork network = configuration.getThisNetwork();
                final String accountUuid = network.getAccount();
                final String cloudUuid = network.getStorage();
                final String ansiblePath = StorageServiceDriver.getUri(cloudUuid, "ansible/");

                final CloudService localStorage = cloudDAO.findByAccountAndId(accountUuid, LOCAL_STORAGE);
                final File localStorageBase = localStorage != null
                        ? new File(json(localStorage.getDriverConfig(), LocalStorageConfig.class).getBaseDir())
                        : die("backup: no cloud service with name "+LOCAL_STORAGE+" found");

                final BubbleBackup existing;
                final String backupPath;
                if (decision.hasQueuedBackup()) {
                    existing = decision.getQueuedBackup();
                    backupPath = existing.getPath();
                } else {
                    backupPath = getBackupPath(network);
                    existing = backupDAO.findByNetworkAndPath(network.getUuid(), backupPath);
                    if (existing != null) {
                        log.warn("backup: backup exists, overwriting: " + json(existing));
                    }
                }
                log.info("backup: using backupPath="+backupPath);

                lockAndDo(backupPath, p -> {
                    final BubbleBackup backup = existing != null
                            ? existing
                            : backupDAO.create(new BubbleBackup()
                            .setNetwork(network.getUuid())
                            .setAccount(network.getAccount())
                            .setPath(p)
                            .setStatus(BackupStatus.backup_in_progress));

                    try {
                        final String home = HOME_DIR;

                        final File jarFile = configuration.getBubbleJar();;
                        if (!jarFile.exists() || jarFile.length() < 10*Bytes.MB) {
                            return die("backup: jarFile not found or too small: "+abs(jarFile));
                        }

                        @Cleanup final TempDir temp = new TempDir();

                        // 1 backup ansible snapshot -- todo: check sha256
                        final File ansibleTgz = new File(home, "ansible.tgz");
                        if (ansibleTgz.exists()) {
                            log.info("backup: backing up ansible archive");
                            backupFile(ansiblePath, ansibleTgz);
                        } else {
                            log.warn("backup: ansible archive not found, not backing up: "+abs(ansibleTgz));
                        }

                        // 2 dump database to storage
                        log.info("backup: backing up DB");
                        final File pgDumpFile = new File(temp, "bubble.sql.gz");
                        configuration.pgDump(pgDumpFile);
                        log.info("backup: dumped DB to "+abs(pgDumpFile));
                        backupFile(backupPath, pgDumpFile);

                        // 3 copy bubble jar to storage
                        log.info("backup: backing up bubble.jar: "+abs(jarFile));
                        backupFile(backupPath, jarFile, "bubble.jar");

                        // 4 copy localStorage to storage
                        log.info("backup: backing up "+LOCAL_STORAGE);
                        backupDir(backupPath+LOCAL_STORAGE, localStorageBase);

                        // 5 copy mitm certs to storage
                        log.info("backup: backing up "+abs(MITMPROXY_CERT_DIR));
                        backupDir(backupPath+MITMPROXY_CERT_DIR.getName(), MITMPROXY_CERT_DIR);

                        // 6 copy .BUBBLE_xyz password files to storage
                        log.info("backup: backing up dotfiles");
                        backupFiles(backupPath+DOTFILES, home, (dir, file) -> file.startsWith(".BUBBLE_"));

                        // 7 write BubbleBackup record to selfNode.json (database has already been backed up!)
                        configuration.getThisNode().setBackup(backup);

                        // 8 copy self_node.json to storage. upon restore we will need to change IP/fqdn, but other data is important
                        log.info("backup: backing up "+SELF_NODE_JSON);
                        final File selfNodeFile = new File(home, SELF_NODE_JSON);
                        backupFile(backupPath, selfNodeFile);

                        // 9 copy sage_node.json to storage, if exists
                        final File sageNodeFile = new File(home, SAGE_NODE_JSON);
                        if (sageNodeFile.exists()) {
                            log.info("backup: backing up " + SAGE_NODE_JSON);
                            backupFile(backupPath, sageNodeFile);
                        }

                        // 10 copy sage_key.json to storage, if exists
                        final File sageKeyFile = new File(home, SAGE_KEY_JSON);
                        if (sageKeyFile.exists()) {
                            log.info("backup: backing up " + SAGE_KEY_JSON);
                            backupFile(backupPath, sageKeyFile);
                        }

                    } catch (Exception e) {
                        backupDAO.update(backup
                                .setStatus(BackupStatus.backup_error)
                                .setError(errorString(e, ERROR_MAXLEN)));
                        return die("backup: "+e);
                    }
                    backupDAO.update(backup.setStatus(BackupStatus.backup_completed));
                    log.info("backup: completed successfully: "+p);

                    if (selfNodeService.hasSageNode()) {
                        log.info("backup: notifying sage of new backup");
                        final BubbleNode sageNode = nodeDAO.findByUuid(selfNodeService.getSageNode().getUuid());
                        if (sageNode == null) {
                            log.warn("backup: sage node not found, cannot notify");
                        } else {
                            final NotificationReceipt receipt = notificationService.notify(sageNode, register_backup, configuration.getThisNode());
                            if (receipt.isSuccess()) {
                                log.info("backup: sage node notified of backup");
                            } else {
                                log.error("backup: sage node refused notification of backup");
                            }
                        }
                    }
                    return p;
                });
            }

        } catch (Exception e) {
            log.error("process: "+e, e);
            sleep(getSleepTime()/2, "waiting to recheck after error: "+e);
        }
    }

    private String getBackupPath(BubbleNetwork network) { return getBackupPath(network, null); }

    private String getBackupPath(BubbleNetwork network, String label) {
        label = empty(label) ? "" : "_" + label.replace("/", "_"); // just in case some slipped through
        final String path = BACKUP_PREFIX + "/" + network.getNetworkDomain() + "_" + DATE_FORMAT_YYYYMMDD.print(now()) + label;
        return StorageServiceDriver.getUri(network.getStorage(), path) + "/";
    }

    private void backupDir(String prefix, File dir) {
        final int pathLen = abs(dir).length();
        new FilesystemWalker()
                .withDir(dir)
                .withVisitor(file -> {
                    if (!file.getName().endsWith(SUFFIX_META) && !abs(file).contains("/"+BACKUP_PREFIX+"/")) {
                        final String dest = abs(file).substring(pathLen + 1, abs(file).length() - file.getName().length());
                        backupFile(prefix + "/" + dest, file);
                    }
                }).walk();
    }

    private boolean backupFiles(String prefix, String path, FilenameFilter filter) {
        final File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) return die("backupFiles: dir not found: "+abs(dir));
        final File[] files = dir.listFiles(filter);
        if (files == null) return die("backupFiles: no matching files found in dir: "+abs(dir));
        for (File f : files) backupFile(prefix, f);
        return true;
    }

    private boolean backupFile(String prefix, File f) { return backupFile(prefix, f, f.getName()); }

    private boolean backupFile(String prefix, File f, String name) {
        if (!f.exists()) return die("backupFile: file not found: " + abs(f));
        try (InputStream in = new FileInputStream(f)) {
            log.info("backupFile: writing prefix="+prefix+", file="+abs(f)+", name="+name);
            storageService.write(configuration.getThisNode().getAccount(), prefix+name, in, new StorageMetadata(f));
        } catch (Exception e) {
            return die("backupFile: error writing to storage: "+e);
        }
        return true;
    }

    private BackupDecision shouldBackup() {

        if (!accountDAO.activated()) return BackupDecision.FALSE;
        final List<BubbleBackup> backups = backupDAO.findByNetwork(configuration.getThisNetwork().getUuid());
        if (backups.isEmpty()) return BackupDecision.TRUE;

        // anything queued?
        final BubbleBackup queued = backups.stream().filter(b -> b.getStatus() == BackupStatus.queued).findFirst().orElse(null);
        if (queued != null) return new BackupDecision(queued);

        // if all backups are older than BACKUP_INTERVAL, then backup now
        final BubbleBackup backup = backups.stream().filter(BubbleBackup::success).findFirst().orElse(null);
        return backup == null || backup.getCtimeAge() > BACKUP_INTERVAL
                ? BackupDecision.TRUE
                : BackupDecision.FALSE;
    }

    public BubbleBackup queueBackup(String label) {
        final BubbleNetwork network = configuration.getThisNetwork();
        final BubbleBackup backup = backupDAO.create(new BubbleBackup()
                .setAccount(network.getAccount())
                .setNetwork(network.getUuid())
                .setPath(getBackupPath(network, label))
                .setLabel(label)
                .setStatus(BackupStatus.queued));
        interrupt();  // wake up SimpleDaemon thread
        return backup;
    }

    @Override protected boolean canInterruptSleep() { return true; }

    private final AtomicReference<String> lock = new AtomicReference<>();

    private String lockAndDo (String path, Function<String, String> func) {
        final String lck = lock.get();
        if (lck != null) {
            log.warn("backup: already locked: "+lck);
            return null;
        }
        synchronized (lock) {
            if (lock.get() != null) {
                log.warn("backup: already locked: "+lock.get());
                return null;
            }
            final String stateKey = getMetadataStateKey();
            final RedisService metadata = getBackupMetadata();
            try {
                // what is the state of redis?
                lock.set(metadata.lock(stateKey, BR_STATE_LOCK_TIMEOUT, BR_STATE_DEADLOCK_TIMEOUT));
                return func.apply(path);

            } catch (Exception e) {
                log.error("backup: " + e, e);

            } finally {
                if (lock.get() != null) {
                    metadata.unlock(stateKey, lock.get());
                    lock.set(null);
                }
            }
        }
        return null;
    }

}
