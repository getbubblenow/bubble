/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.upgrade;

import bubble.dao.cloud.BubbleBackupDAO;
import bubble.model.cloud.BackupStatus;
import bubble.model.cloud.BubbleBackup;
import bubble.model.cloud.BubbleVersionInfo;
import bubble.notify.upgrade.JarUpgradeNotification;
import bubble.server.BubbleConfiguration;
import bubble.service.backup.BackupService;
import bubble.service.boot.JarUpgradeMonitor;
import bubble.service.boot.StandardSelfNodeService;
import bubble.service.notify.NotificationService;
import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;

import static bubble.ApiConstants.*;
import static bubble.model.cloud.notify.NotificationType.upgrade_request;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH_mm_ss_SSS;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;

@Service @Slf4j
public class BubbleJarUpgradeService {

    private static final long PRE_UPGRADE_BACKUP_TIMEOUT = MINUTES.toMillis(20);

    @Autowired private BubbleConfiguration configuration;
    @Autowired private BackupService backupService;
    @Autowired private BubbleBackupDAO backupDAO;
    @Autowired private NotificationService notificationService;
    @Autowired private RedisService redis;
    @Autowired private StandardSelfNodeService selfNodeService;

    @Getter(lazy=true) private final RedisService nodeUpgradeRequests = redis.prefixNamespace(getClass().getName());

    public String registerNodeUpgrade(String nodeUuid) {
        final String key = randomAlphanumeric(10) + "." + now();
        getNodeUpgradeRequests().set(key, nodeUuid, EX, MINUTES.toSeconds(1));
        return key;
    }

    public String getNodeForKey(String key) { return getNodeUpgradeRequests().get(key); }

    // set to 'false' for faster debugging of upgrade process
    private static final boolean BACKUP_BEFORE_UPGRADE = true;

    public void upgrade() {
        if (!configuration.getJarUpgradeAvailable()) {
            log.warn("upgrade: No upgrade available, returning");
            return;
        }

        final BubbleVersionInfo sageVersion = configuration.getSageVersion();

        if (BACKUP_BEFORE_UPGRADE) {
            final String currentVersion = configuration.getShortVersion();
            final String newVersion = sageVersion.getShortVersion();
            BubbleBackup bubbleBackup = backupService.queueBackup("before_upgrade_" + currentVersion + "_to_" + newVersion + "_on_" + DATE_FORMAT_YYYY_MM_DD_HH_mm_ss_SSS.print(now()));

            // monitor backup, ensure it completes
            final long start = now();
            while (bubbleBackup.getStatus() != BackupStatus.backup_completed && now() - start < PRE_UPGRADE_BACKUP_TIMEOUT) {
                sleep(SECONDS.toMillis(5), "waiting for backup to complete before upgrading");
                bubbleBackup = backupDAO.findByUuid(bubbleBackup.getUuid());
            }
            if (bubbleBackup.getStatus() != BackupStatus.backup_completed) {
                log.warn("upgrade: timeout waiting for backup to complete, status=" + bubbleBackup.getStatus());
                return;
            }
        }

        final File upgradeJar = new File(HOME_DIR, "upgrade.jar");
        if (upgradeJar.exists()) {
            log.error("upgrade: jar already exists, not upgrading: "+abs(upgradeJar));
            return;
        }

        final JarUpgradeMonitor jarUpgradeMonitor = selfNodeService.getJarUpgradeMonitorBean();
        jarUpgradeMonitor.downloadJar(upgradeJar, sageVersion);
    }
}
