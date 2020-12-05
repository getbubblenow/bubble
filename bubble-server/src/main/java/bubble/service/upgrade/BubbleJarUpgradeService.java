/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.service.upgrade;

import bubble.dao.account.AccountDAO;
import bubble.dao.cloud.BubbleBackupDAO;
import bubble.model.account.Account;
import bubble.model.cloud.BackupStatus;
import bubble.model.cloud.BubbleBackup;
import bubble.model.cloud.BubbleVersionInfo;
import bubble.server.BubbleConfiguration;
import bubble.service.backup.BackupService;
import bubble.service.boot.JarUpgradeMonitor;
import bubble.service.boot.StandardSelfNodeService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.SimpleDaemon;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static bubble.ApiConstants.HOME_DIR;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYY_MM_DD_HH_mm_ss_SSS;
import static org.cobbzilla.wizard.cache.redis.RedisService.EX;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Service @Slf4j
public class BubbleJarUpgradeService extends SimpleDaemon {

    private static final long PRE_UPGRADE_BACKUP_TIMEOUT = MINUTES.toMillis(20);

    @Autowired private BubbleConfiguration configuration;
    @Autowired private BackupService backupService;
    @Autowired private BubbleBackupDAO backupDAO;
    @Autowired private AccountDAO accountDAO;
    @Autowired private RedisService redis;
    @Autowired private StandardSelfNodeService selfNodeService;

    private final AtomicBoolean enabled = new AtomicBoolean(true);
    public void enable() { enabled.set(true); }
    public void disable() { enabled.set(false); }
    public boolean pause() {
        boolean b = enabled.get();
        enabled.set(false);
        return b;
    }
    public void restore(boolean b) { enabled.set(b); }

    @Getter(lazy=true) private final RedisService nodeUpgradeRequests = redis.prefixNamespace(getClass().getName());

    public String registerNodeUpgrade(String nodeUuid) {
        final String key = randomAlphanumeric(10) + "." + now();
        getNodeUpgradeRequests().set(key, nodeUuid, EX, MINUTES.toSeconds(1));
        return key;
    }

    public String getNodeForKey(String key) { return getNodeUpgradeRequests().get(key); }

    // For a node, when to run:
    // - delayed start
    // - sleep just less than one hour
    // - only run during one hour of the day, as measured by the local time

    @Override protected long getStartupDelay() { return MINUTES.toMillis(1); }
    @Override protected boolean canInterruptSleep() { return true; }

    @Getter private final long sleepTime = MINUTES.toMillis(2);

    // todo: make this configurable
    public static final int UPGRADE_HOUR_OF_DAY = 6;

    @Override protected void process() {
        log.info("process: starting upgrade check");
        if (!shouldRun()) return;

        log.info("process: checking/upgrading bubble jar...");
        try {
            upgrade();
        } catch (Exception e) {
            reportError("upgrade error: " + shortError(e), e);
            log.error("process: upgrade error: " + shortError(e));
        }
    }

    public boolean shouldRun() {
        if (!enabled.get()) {
            log.warn("shouldRun: upgrades not currently enabled, returning");
            return false;
        }

        if (!configuration.getJarUpgradeAvailable()) {
            log.warn("shouldRun: no upgrade available, returning");
            return false;
        }

        // we shouldn't really need to adjust for the timezone here, because the Bubble
        // should have set the operating system timezone to be the same during ansible setup.
        // but just to be safe, use the bubble's time zone here. we have it handy.
        final DateTimeZone dtz = DateTimeZone.forID(configuration.getThisNetwork().getTimezone());
        final DateTime dateTime = new DateTime(now(), dtz);
        final int hour = dateTime.hourOfDay().get();
        if (hour != UPGRADE_HOUR_OF_DAY) {
            log.warn("shouldRun: hour of day ("+hour+") != UPGRADE_HOUR_OF_DAY ("+UPGRADE_HOUR_OF_DAY+"), returning");
            return false;
        }

        // OK, it's that special hour of the day. does the admin even want updates?
        final Account account = accountDAO.getFirstAdmin();
        if (account == null || !account.getAutoUpdatePolicy().jarUpdates()) {
            log.warn("shouldRun: account is null or auto-update policy does not allow jar updates");
            return false;
        }
        log.info("shouldRun: returning true");
        return true;
    }

    // set to 'false' for faster debugging of upgrade process
    private static final boolean BACKUP_BEFORE_UPGRADE = true;

    public synchronized boolean upgrade() {
        if (!configuration.getJarUpgradeAvailable()) {
            log.warn("upgrade: no upgrade available, returning");
            return false;
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
                return false;
            }
        }

        final File upgradeJar = new File(HOME_DIR, "upgrade.jar");
        if (upgradeJar.exists()) {
            log.error("upgrade: jar already exists, not upgrading: "+abs(upgradeJar));
            return false;
        }

        final JarUpgradeMonitor jarUpgradeMonitor = selfNodeService.getJarUpgradeMonitorBean();
        jarUpgradeMonitor.downloadJar(upgradeJar, sageVersion);
        return true;
    }
}
